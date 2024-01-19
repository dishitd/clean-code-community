package services.ftlcontractservices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import constants.AssignType;
import constants.NotificationConstants;
import controllers.VendorProductManagement;
import dao.CouriersDBDao;
import dao.CustomerRepoDBDao;
import dao.CustomersDBDao;
import dao.ProductsDBDao;
import dao.UsersDBDao;
import dao.UtilDBDao;
import dao.VendorRepoDBDao;
import exceptions.DataNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import models.CustomerNotification;
import models.SendMyKartDAO;
import models.ServiceModule;
import models.SessionData;
import models.Vendor;
import models.VendorNotification;
import models.fulltruckload.ContractApprovalRequest;
import models.fulltruckload.FtlContractAssignRequest;
import models.fulltruckload.Quotation;
import models.vendororderbooking.ContractResponse;
import models.vendororderbooking.ContractResponse.Customer.Billing;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDateTime;
import org.bson.BsonDouble;
import org.bson.Document;
import services.NotificationService;
import util.BasicUtil;
import util.ObjectMapperUtil;
import util.TimeUtils;

import static constants.ContractEditConstants.CONTRACT_AGAINST_QUOTATION;
import static constants.GenericConstants.BY;
import static constants.GenericConstants.DUE_TO;
import static constants.GenericConstants.HAS_BEEN_ADDED_BY;
import static constants.GenericConstants.NEW_CONTRACT_ADDED;
import static constants.GenericConstants.REJECTED_BY_CUSTOMER;
import static constants.MongoDatabaseKeyNames.ADD_TO_SET_OPERATOR;
import static constants.MongoDatabaseKeyNames.INCREMENT_OPERATOR;
import static constants.MongoDatabaseKeyNames.PULL_OPERATOR;
import static constants.MongoDatabaseKeyNames.PUSH_OPERATOR;
import static constants.MongoDatabaseKeyNames.SET_OPERATOR;
import static constants.MongoDatabaseKeyNames.TIME_CREATED;
import static constants.NotificationConstants.NOTIFICATION_PREFIX;
import static constants.SystemConstants.CUSTOMER_RECIPIENT;
import static constants.SystemConstants.DIRECT_APPROVAL;
import static constants.SystemConstants.QUOTATION_APPROVAL;
import static constants.SystemConstants.VENDOR_RECIPIENT;

public class FtlContractOperation {

    @Inject
    private VendorProductManagement vendorProductManagement;
    @Inject
    private ProductsDBDao productsDBDao;
    @Inject
    private UtilDBDao utilDBDao;
    @Inject
    private SendMyKartDAO sendMyKartDAO;
    @Inject
    private CustomerRepoDBDao customerRepoDBDao;
    @Inject
    private UsersDBDao usersDBDao;
    @Inject
    private CustomersDBDao customersDBDao;
    @Inject
    private VendorRepoDBDao vendorRepoDBDao;
    @Inject
    private CouriersDBDao couriersDBDao;
    @Inject
    private NotificationService notificationService;

    private static final String CONTRACT_ID = "contractId";
    private static final String COMPANY_LOGO = "companyLogo";
    private static final String COMPANY_NAME = "companyName";
    private static final String PRODUCTS = "products";
    private static final String PRODUCTS_DOT_PID = "products.pId";
    private static final String UNAPPROVED_PRODUCTS = "unapprovedProducts";
    private static final String COMPANY_ID = "companyId";
    private static final String BILLING = "billing";
    private static final String VENDOR_ID = "vendorId";
    private static final String PRODUCT_NAME = "productName";
    private static final String CUSTOMERS = "customers";
    private static final String VENDOR_NAME = "vendorName";
    private static final String VENDOR_COMPANY_ID = "vendorCId";
    private static final String CUSTOMER_ID = "customerId";
    private static final String REJECTION_TEXT = "Service edit rejected by customer ";
    private static final String APPROVED = "approved";
    private static final String ADMIN = "admin";
    private static final String NOTIFICATIONS = "notifications";

    public void assignFtlContractToCustomer(SessionData sessionData, JsonNode json) throws JsonProcessingException {
        FtlContractAssignRequest request = ObjectMapperUtil.fetchObjectMapper().treeToValue(json, FtlContractAssignRequest.class);
        if (request.getAssignType() == AssignType.CUSTOMER_ASSIGN) {
            assignContractAgainstCustomer(sessionData, request);
        } else if (request.getAssignType() == AssignType.QUOTATION_ASSIGN) {
            assignContractAgainstQuotation(sessionData, request);
        }
    }

    public void approveFtlContract(SessionData sessionData, JsonNode json) throws JsonProcessingException {
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        String logo = "";
        ContractApprovalRequest approvalRequest = ObjectMapperUtil.fetchObjectMapper().treeToValue(json, ContractApprovalRequest.class);
        String vendorCollectionName = sendMyKartDAO.getVendorCollectionIdFromCompanyId(approvalRequest.getVendorId());
        if (isActionAlreadyTaken(collectionName, companyId, approvalRequest.getContractId(), approvalRequest.getVendorId())) {
            throw new DataNotFoundException("Action already taken" + approvalRequest.getContractId());
        }
        Document contractList = customerRepoDBDao.retrieveContractList(collectionName, companyId);
        logo = contractList.getString("logo");
        List<Document> unapprovedProducts = retrieveUnApprovedContracts(contractList, approvalRequest.getContractId());
        if (unapprovedProducts.isEmpty()) {
            throw new DataNotFoundException("Product not in unapproved list" + approvalRequest.getContractId());
        }
        performContractApprovalOperation(sessionData, unapprovedProducts, contractList, logo, approvalRequest,
                vendorCollectionName);
        updateFtlContractStatusEnabled(vendorCollectionName, approvalRequest, collectionName);
    }

    private void updateFtlContractStatusEnabled(String vendorCollectionName, ContractApprovalRequest approvalRequest, String collectionName) {
        Document enabledContract = productsDBDao.retrieveEnabledContract(vendorCollectionName, approvalRequest.getContractId());
        customerRepoDBDao.updateFtlContractEnableStatus(enabledContract, approvalRequest.getContractId(), collectionName);
    }

    private boolean isActionAlreadyTaken(String collectionName, String companyId, String contractId, String vendorId) {
        Document checkDocument = fetchDocumentToCheck(collectionName, companyId, contractId, vendorId);
        List<Document> duplicateDocuments = customerRepoDBDao.fetchDuplicateDocuments(collectionName, checkDocument);
        return BasicUtil.isCollectionNotNullOrEmpty(duplicateDocuments);
    }

    private void assignContractAgainstQuotation(SessionData sessionData, FtlContractAssignRequest request) throws JsonProcessingException {
        String companyId = sessionData.getCompanyId();
        Document quotationDocument = retrieveQuotationDocument(companyId, request);
        String customerId = quotationDocument.getString(CUSTOMER_ID);
        String customerCollection = quotationDocument.getString(COMPANY_ID);
        String customerName = quotationDocument.getString(COMPANY_NAME);
        ContractResponse.Customer.Billing billing = retrieveCustomerBillingModel(request, companyId, customerId,
                customerCollection, customerName);
        billing.setType(QUOTATION_APPROVAL);
        Document customerDocument = fetchCustomerContractDocument(billing, customerName, customerId, customerCollection);
        Document quotationData = fetchQuotationData(request, billing,customerName,customerId,customerCollection);
        updateQuotationInProductsDB(request.getContractId(),sessionData.getCollectionName(),quotationData);
        updateDatabasesForContractAssignment(request, customerId, sessionData, customerDocument, QUOTATION_APPROVAL);
        sendContractAssignmentNotification(request, sessionData, customerDocument);
    }

    private void updateQuotationInProductsDB(String contractId, String collectionName, Document quotationData) {
        productsDBDao.updateQuotationData(contractId,collectionName,quotationData);
    }

    private Document fetchQuotationData(FtlContractAssignRequest request, ContractResponse.Customer.Billing billing,
                                        String customerName, String customerId, String customerCollection) throws JsonProcessingException {
        Quotation quotation = new Quotation();
        mapToCreditInformation(quotation, request, billing);
        mapToBillingInformation(quotation, request, billing);
        mapToPaymentInformation(quotation, request, billing);
        mapToQuotationCustomerDetails(quotation, customerId, customerName, customerCollection);
        return Document.parse(ObjectMapperUtil.fetchObjectMapper().writeValueAsString(quotation));
    }

    private void mapToQuotationCustomerDetails(Quotation quotation, String customerId, String customerName, String customerCollection) {
        quotation.setCustomerId(customerId);
        quotation.setCompanyName(customerName);
        quotation.setCompanyId(customerCollection);
    }

    private void mapToPaymentInformation(Quotation quotation, FtlContractAssignRequest request, Billing billing) {
        quotation.setPaymentType(billing.getPaymentType());
        quotation.setPaymentTime(billing.getPaymentTime());
        quotation.setPrepaidPercent(billing.getPrepaidPercent());
        quotation.setPrepaidPercentOn(billing.getPrepaidPercentOn());
    }

    private void mapToBillingInformation(Quotation quotation, FtlContractAssignRequest request, Billing billing) {
        quotation.setBillingInterestValue(billing.getBillingInterestValue());
        quotation.setBillingInterestOn(billing.getBillingInterestOn());
        quotation.setBillingInterestDays((int) billing.getBillingInterestDays());
        quotation.setBillingVendorId(billing.getBillingVId());
        quotation.setBillingScheduleType(billing.getScheduleType());
        quotation.setBillingDate(billing.getBillingDate());
        quotation.setBillingRecursive(billing.getBillingRecursiveDays());
    }

    private void mapToCreditInformation(Quotation quotation, FtlContractAssignRequest request, Billing billing) {
        quotation.setId(request.getQuotations());
        quotation.setCreditAmount(billing.getCreditAmount());
        quotation.setCreditAmountAssigned(billing.getCreditAmountAssigned());
        quotation.setCreditLoadAmount(billing.getCreditLoad());
        quotation.setCreditLoadAssigned(billing.getCreditLoadAssigned());
    }

    private Document fetchCustomerContractDocument(ContractResponse.Customer.Billing billing, String customerName,
                                                   String customerId, String customerCollection) throws JsonProcessingException {
        ContractResponse.Customer customer = new ContractResponse.Customer();
        customer.setBilling(billing);
        customer.setApproved(false);
        customer.setCustomerName(customerName);
        customer.setCustomerId(customerId);
        customer.setCustomerCId(customerCollection);
        return Document.parse(ObjectMapperUtil.fetchObjectMapper().writeValueAsString(customer));
    }

    private Document retrieveQuotationDocument(String vendorCompanyId, FtlContractAssignRequest request) {
        Document searchQuery = new Document("id", request.getQuotations()).append("vendors.vId", vendorCompanyId);
        Document updateQuery = new Document(SET_OPERATOR, new Document("vendors.$.responded", true)
                .append("vendors.$.timeResponded", new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()))
                .append("vendors.$.pId", request.getContractId()).append("vendors.$.productName", request.getContractName()));
        return utilDBDao.findOneQuotationAndUpdate(searchQuery, updateQuery);
    }

    private void assignContractAgainstCustomer(SessionData sessionData, FtlContractAssignRequest request) throws JsonProcessingException {
        String companyId = sessionData.getCompanyId();
        String customerId = request.getCustomers();
        String customerCollection = sendMyKartDAO.getCustomerCollectionIdFromCompanyId(customerId);
        String customerName = sendMyKartDAO.getCustomerCompanyNameFromCompanyId(customerId);
        ContractResponse.Customer.Billing billing = retrieveCustomerBillingModel(request, companyId, customerId, customerCollection, customerName);
        billing.setType(DIRECT_APPROVAL);
        Document customerDocument = fetchCustomerContractDocument(billing, customerName, customerId, customerCollection);
        updateDatabasesForContractAssignment(request, request.getCustomers(), sessionData, customerDocument, DIRECT_APPROVAL);
        sendContractAssignmentNotification(request, sessionData, customerDocument);
    }

    private void sendContractAssignmentNotification(FtlContractAssignRequest request, SessionData sessionData, Document customerDoc) {
        vendorProductManagement.sendAssignProductNotification(CUSTOMERS,
                (Document) customerDoc.get(BILLING), sessionData.getCompanyName(), sessionData.getCompanyId(),
                sessionData.getCompanyId(), sessionData.getUserName(), request.getContractId(), request.getContractName());
    }

    private void updateDatabasesForContractAssignment(FtlContractAssignRequest request, String customerId, SessionData sessionData,
                                                      Document customerDocument, String assignmentType) {
        String vendorCollection = sessionData.getCollectionName();
        String customerCollection = sendMyKartDAO.getCustomerCollectionIdFromCompanyId(customerId);
        removeCustomerFromProduct(vendorCollection, request.getContractId(), customerId);
        removeExistingCustomerFromProduct(vendorCollection, request, customerId);
        Document product = productsDBDao.findOneDocumentAndUpdate(vendorCollection, request.getContractId(), customerDocument);
        if (product.isEmpty()) {
            throw new DataNotFoundException("Resources not found" + request.getContractId());
        }
        Document unapprovedProduct = retrieveUnapprovedProductDocument(request, sessionData, product, customerDocument);
        unapprovedProduct.put("type", assignmentType);
        unapprovedProduct.put("quotationId",request.getQuotations());
        updateCustomerRepoForContractAssignment(customerCollection, customerId, request, unapprovedProduct);
    }

    private void removeExistingCustomerFromProduct(String vendorCollection, FtlContractAssignRequest request, String customerId) {
        productsDBDao.updateOneDocument(vendorCollection, findByContractId(request.getContractId()),
                fetchExistingCustomerFromContract(customerId));
    }

    private Document fetchExistingCustomerFromContract(String customerId) {
        return new Document(PULL_OPERATOR, findCustomerByCustomerId(customerId));
    }

    private Document findCustomerByCustomerId(String customerId) {
        return new Document(CUSTOMERS, new Document(CUSTOMER_ID, customerId));
    }

    private Document findByContractId(String contractId) {
        return new Document(CONTRACT_ID, contractId);
    }

    private void updateCustomerRepoForContractAssignment(String customerCollection, String customerId,
                                                         FtlContractAssignRequest request, Document unapprovedProduct) {
        removePreviousExistingProductFromUnapprovedList(customerCollection, customerId, request);
        addNewContractToUnapprovedList(customerCollection, customerId, unapprovedProduct);
        removeProductFromApprovedList(customerCollection, customerId, request);
    }

    private void removeProductFromApprovedList(String customerCollection, String customerId, FtlContractAssignRequest request) {
        customerRepoDBDao.updateOneDocument(customerCollection, fetchApprovedContractByContractId(customerId, request.getContractId()),
                fetchApprovedContractDocument(request.getContractId()));
    }

    private Document fetchApprovedContractDocument(String contractId) {
        return new Document(PULL_OPERATOR,
                new Document(PRODUCTS, new Document("pId", contractId)));
    }

    private Document fetchApprovedContractByContractId(String customerId, String contractId) {
        return new Document("cId", customerId).append(PRODUCTS_DOT_PID, contractId);
    }

    private void addNewContractToUnapprovedList(String customerCollection, String customerId,
                                                Document unapprovedProduct) {
        customerRepoDBDao.updateOneDocument(customerCollection, findByCustomerId(customerId),
                fetchNewUnapprovedContract(unapprovedProduct));
    }

    private Document fetchNewUnapprovedContract(Document unapprovedProduct) {
        return new Document(PUSH_OPERATOR, new Document(UNAPPROVED_PRODUCTS, unapprovedProduct));
    }

    private Document findByCustomerId(String customerId) {
        return new Document("cId", customerId);
    }

    private void removePreviousExistingProductFromUnapprovedList(String customerCollection, String customerId, FtlContractAssignRequest request) {
        customerRepoDBDao.updateOneDocument(customerCollection, findByUnapprovedContractId(customerId, request.getContractId()),
                fetchExistingUnapprovedContract(request.getContractId()));
    }

    private Document fetchExistingUnapprovedContract(String contractId) {
        return new Document(PULL_OPERATOR,
                new Document(UNAPPROVED_PRODUCTS, new Document("pId", contractId)));
    }

    private Document findByUnapprovedContractId(String customerId, String contractId) {
        return new Document("cId", customerId).append("unapprovedProducts.pId", contractId);
    }

    private void removeCustomerFromProduct(String vendorCollection, String contractId, String customerId) {
        productsDBDao.updateOneDocument(vendorCollection, new Document(CONTRACT_ID, contractId),
                fetchExistingCustomerFromContract(customerId));
    }

    private ContractResponse.Customer.Billing retrieveCustomerBillingModel(FtlContractAssignRequest request, String companyId,
                                                                           String customerId, String customerCollection, String customerName) {
        ContractResponse.Customer.Billing billing = new ContractResponse.Customer.Billing();
        billing.setPaymentType(request.getPaymentType());
        billing.setPaymentTime(request.getPaymentTime());
        billing.setPrepaidPercent(request.getPrepaidAmount());
        billing.setCreditAmount(request.getCreditAmount());
        billing.setCreditAmountAssigned(request.getCreditAmount());
        billing.setScheduleType(request.getBillingScheduleType());
        billing.setBillingInterestValue(request.getBillingInterestValue());
        billing.setBillingInterestOn(request.getBillingInterestOn());
        billing.setBillingInterestDays(request.getBillingInterestDays());
        billing.setPrepaidPercentOn(request.getPrepaidPercentOn());
        billing.setBillingVId(companyId);
        billing.setCustomerId(customerId);
        billing.setCompanyId(customerCollection);
        billing.setCompanyName(customerName);
        if (request.getBillingScheduleType().equalsIgnoreCase("date")) {
            billing.setBillingDate(request.getBillingDate());
        } else if (request.getBillingScheduleType().equalsIgnoreCase("recursive")) {
            billing.setBillingRecursiveDays(request.getBillingRecursive());
        }
        return billing;
    }

    private Document retrieveUnapprovedProductDocument(FtlContractAssignRequest request, SessionData sessionData, Document product, Document customer) {
        Document unapprovedProduct = new Document();
        unapprovedProduct.put(BILLING, customer.get(BILLING));
        unapprovedProduct.put(VENDOR_ID, product.getString(VENDOR_ID));
        unapprovedProduct.put(VENDOR_NAME, product.getString(COMPANY_NAME));
        unapprovedProduct.put(VENDOR_COMPANY_ID, sessionData.getCollectionName());
        unapprovedProduct.put("vendorLogo", product.getString(COMPANY_LOGO));
        unapprovedProduct.put("pId", request.getContractId());
        unapprovedProduct.put(PRODUCT_NAME, request.getContractName());
        unapprovedProduct.put("productContactEmail", request.getContractName());
        unapprovedProduct.put("productType", ServiceModule.FTL.value);
        unapprovedProduct.put("approvalType", "new");
        unapprovedProduct.put("enabled", product.getBoolean("isContractEnabled"));
        unapprovedProduct.put("creditUsed", 0.0);
        unapprovedProduct.put("date", new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()));
        return unapprovedProduct;
    }  

    private Document fetchDocumentToCheck(String collectionName, String companyId, String contractId, String vendorId) {
        Document checkDocument = new Document();
        checkDocument.append(COMPANY_ID, collectionName);
        checkDocument.append("cId", companyId);
        checkDocument.append(PRODUCTS_DOT_PID, contractId);
        checkDocument.append("products.vId", vendorId);
        return checkDocument;
    }

    private List<Document> retrieveUnApprovedContracts(Document contractList, String contractId) {
        return ((List<Document>) contractList.get(UNAPPROVED_PRODUCTS)).stream()
                .filter(unapprovedContract -> unapprovedContract.getString("pId").equalsIgnoreCase(contractId))
                .collect(Collectors.toList());
    }

    private boolean isVendorAlreadyAddedToContract(String vendorId, Document contractList) {
        return ((List<Document>) contractList.get("vendors")).stream()
                .anyMatch(vendor -> StringUtils.equalsIgnoreCase(vendor.getString("vId"), vendorId));
    }

    private void performContractApprovalOperation(SessionData sessionData, List<Document> unapprovedProducts,
                                                  Document contractList, String logo, ContractApprovalRequest approvalRequest,
                                                  String vendorCollectionName) {
        for (Document product : unapprovedProducts) {
            CustomerNotification customerNotification = new CustomerNotification();
            VendorNotification vendorNotification = new VendorNotification();
            String productType = product.getString("approvalType");
            if (StringUtils.equalsIgnoreCase(productType, "new")) {
                product.put("logo", logo);
                performContractApprovalOnNewOperation(product, sessionData, approvalRequest, vendorNotification, customerNotification,
                        contractList, vendorCollectionName);
            } else {
                updateExistingContractForApproval(approvalRequest, sessionData, product);
                updateExistingContractForRejectionWithReason(approvalRequest, vendorCollectionName, product,
                        customerNotification, vendorNotification, sessionData);
            }
        }
    }

    private void performContractApprovalOnNewOperation(Document product, SessionData sessionData, ContractApprovalRequest approvalRequest,
                                                       VendorNotification vendorNotification, CustomerNotification customerNotification,
                                                       Document contractList, String vendorCollectionName) {
        String contractApprovalType = product.getString("type");
        boolean isVendorAlreadyAdded = isVendorAlreadyAddedToContract(approvalRequest.getVendorId(), contractList);
        ContractApprovalSupplier contractApprovalSupplier = new ContractApprovalSupplier();
        FtlContractService ftlContractService = contractApprovalSupplier.supplyContractApprovalType(contractApprovalType);
        Document approvalDocument = ftlContractService.performContractApproval(product, approvalRequest, isVendorAlreadyAdded
                , sessionData, vendorCollectionName);
        updateContractLogsToProductsDb(vendorCollectionName,product.getString("pId"), (Document) approvalDocument.get("logs"));
        updateContractNotificationDocument(approvalDocument, vendorNotification, customerNotification);
        updateNewContractForApproval(approvalRequest, product, vendorCollectionName, sessionData, isVendorAlreadyAdded,
                vendorNotification);
        updateNewContractForRejection(approvalRequest, vendorNotification, vendorCollectionName, sessionData,
                product, customerNotification);
    }

    private void updateContractLogsToProductsDb(String vendorCollectionName, String pId, Document logs) {
        productsDBDao.updateContractsLogs(vendorCollectionName,pId,logs);
    }

    private void updateContractNotificationDocument(Document approvalDocument, VendorNotification vendorNotification, CustomerNotification customerNotification) {
        vendorNotification.setText(approvalDocument.getString("text"));
        customerNotification.setTitle(approvalDocument.getString("title"));
        vendorNotification.setTitle(approvalDocument.getString("title"));
    }

    private void updateExistingContractForRejectionWithReason(ContractApprovalRequest approvalRequest,
                                                              String vendorCollectionName, Document product,
                                                              CustomerNotification customerNotification,
                                                              VendorNotification vendorNotification, SessionData sessionData) {
        Document log = new Document();
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        String companyName = sessionData.getCompanyName();
        if (isContractApprovalRejected(approvalRequest)) {
            log.append("text", REJECTION_TEXT + companyName + " due to " + approvalRequest.getReasonForRejection());
            log.append("time", TimeUtils.retrieveCurrentTimeInMillis());
            productsDBDao.updateContractAssignmentRejectedDetails(vendorCollectionName, approvalRequest.getContractId(),
                    companyId, log);
            Document repoUpdate = new Document();
            repoUpdate.append(PULL_OPERATOR, new Document(UNAPPROVED_PRODUCTS, new Document("pId", approvalRequest.getContractId())));
            customerRepoDBDao.updateOneDocument(collectionName, new Document("cId", companyId), repoUpdate);
            updateCustomerNotificationDocumentForRejectedContractByReason(sessionData, product,
                    approvalRequest.getContractId(), approvalRequest.getReasonForRejection(), customerNotification);
            updateVendorNotificationDocumentForRejectedContractByReason(sessionData, product, approvalRequest.getContractId()
                    , vendorNotification, vendorCollectionName);
        }
    }

    private boolean isContractApprovalRejected(ContractApprovalRequest approvalRequest) {
        return !approvalRequest.getCustomerResponse().equalsIgnoreCase(APPROVED);
    }

    private void updateExistingContractForApproval(ContractApprovalRequest approvalRequest, SessionData sessionData, Document product) {
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        if (approvalRequest.getCustomerResponse().equalsIgnoreCase(APPROVED)) {
            product.remove("edits");
            Document searchQuery = new Document("cId", companyId)
                    .append("unapprovedProducts.pId", approvalRequest.getContractId());
            Document documentToBeUpdated = new Document(PUSH_OPERATOR, new Document(PRODUCTS, product))
                    .append(PULL_OPERATOR, new Document(UNAPPROVED_PRODUCTS, new Document("pId", approvalRequest.getContractId())));
            customerRepoDBDao.updateOneDocument(collectionName, searchQuery, documentToBeUpdated);
        }
    }

    private void updateNewContractForRejection(ContractApprovalRequest approvalRequest, VendorNotification vendorNotification,
                                               String vendorCollectionName, SessionData sessionData, Document product, CustomerNotification customerNotification) {
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        String companyName = sessionData.getCompanyName();
        if (isContractApprovalRejected(approvalRequest)) {
            Document repoUpdate = new Document();
            repoUpdate.append(PULL_OPERATOR, new Document(UNAPPROVED_PRODUCTS, new Document("pId", approvalRequest.getContractId())));
            customerRepoDBDao.updateOneDocument(collectionName, new Document("cId", companyId), repoUpdate);
            updateCustomerNotificationDocumentForRejectedContractByReason(sessionData, product, approvalRequest.getContractId(),
                    approvalRequest.getReasonForRejection(), customerNotification);
            updateVendorNotificationDocumentForRejectedContractByReason(sessionData, product, approvalRequest.getContractId(),
                    vendorNotification, vendorCollectionName);
            String quotationId = product.getString("quotationId");
            Document log = mapToLogDocument(quotationId, approvalRequest, companyName);
            productsDBDao.updateQuotationDetailsAgainstContract(vendorCollectionName, approvalRequest.getContractId(), companyId, log,
                    quotationId);
            vendorRepoDBDao.updateQuotationRejectionDetails(vendorCollectionName, quotationId, product);
        }
    }

    private Document mapToLogDocument(String quotationId, ContractApprovalRequest approvalRequest, String companyName) {
        Document log = new Document();
        log.append("text", retrieveRejectionTextForQuotation(quotationId, companyName, approvalRequest));
        log.append("time", TimeUtils.retrieveCurrentTimeInMillis());
        return log;
    }

    private String retrieveRejectionTextForQuotation(String quotationId, String companyName, ContractApprovalRequest approvalRequest) {
        return CONTRACT_AGAINST_QUOTATION + quotationId + REJECTED_BY_CUSTOMER + companyName + DUE_TO
                + approvalRequest.getReasonForRejection();
    }

    private void updateNewContractForApproval(ContractApprovalRequest approvalRequest, Document product,
                                              String vendorCollectionName, SessionData sessionData,
                                              boolean isVendorAlreadyAdded, VendorNotification vendorNotification) {
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        if (approvalRequest.getCustomerResponse().equalsIgnoreCase(APPROVED)) {
            updateBillingDocumentForNewContractApproval(product, vendorCollectionName, approvalRequest.getContractId(), companyId);
            updatePincodeContractList(collectionName, approvalRequest, vendorCollectionName);
            product.put("creditUsed", new BsonDouble(0));
            updateCustomerRepoWithContractDetails(product, vendorCollectionName, sessionData,
                    approvalRequest.getVendorId(), approvalRequest.getContractId(), isVendorAlreadyAdded);
            updateNotificationForNewContractApproval(product, sessionData, approvalRequest, vendorNotification, collectionName,
                    vendorCollectionName, companyId);
            updateVendorRepoForNewContractApproval(approvalRequest, product, sessionData, vendorCollectionName, isVendorAlreadyAdded);
        }
    }

    private void updateVendorRepoForNewContractApproval(ContractApprovalRequest approvalRequest, Document product, SessionData sessionData,
                                                        String vendorCollectionName, boolean isVendorAlreadyAdded) {
        Document vendorUpdate = retrieveVendorUpdateDocument(approvalRequest.getContractId(), product.getString("logo"),
                sessionData, product, isVendorAlreadyAdded);
        String quotationId = product.getString("quotationId");
        vendorRepoDBDao.updateQuotationAgainstContract(vendorUpdate, vendorCollectionName, quotationId, product);
    }

    private void updateNotificationForNewContractApproval(Document product, SessionData sessionData, ContractApprovalRequest approvalRequest,
                                                          VendorNotification vendorNotification, String collectionName,
                                                          String vendorCollectionName, String companyId) {
        String userName = sessionData.getUserName();
        String userFullName = sessionData.getUserFullName();
        Document customerNotificationDocument = retrieveFtlCustomerNotificationDocument(userName, product, approvalRequest.getContractId(),
                companyId, userFullName);
        updateFtlCustomerNotificationDocument(customerNotificationDocument, collectionName, companyId);
        updateVendorNotifications(sessionData, approvalRequest.getContractId(), vendorCollectionName, product, vendorNotification);
    }

    private void updateBillingDocumentForNewContractApproval(Document product, String vendorCollectionName, String contractId, String companyId) {
        Document billing = (Document) product.get(BILLING);
        billing.put("type", product.getString("type"));
        Document log = new Document();
        billing.remove("id");
        productsDBDao.updateContractBillingDetails(vendorCollectionName, contractId, log, billing, companyId);
    }

    private Document retrieveVendorUpdateDocument(String contractId, String logo, SessionData sessionData,
                                                  Document product, boolean isVendorAlreadyAdded) {
        Document vendorUpdate = fetchVendorUpdateDocument(contractId, product);
        if (!isVendorAlreadyAdded) {
            updateVendorDocument(vendorUpdate, sessionData, logo);
        }
        return vendorUpdate;
    }

    private Document fetchVendorUpdateDocument(String contractId, Document product) {
        return new Document(SET_OPERATOR, findQuotationAssignedContract()
                .append("quotations.$.pId", contractId)
                .append("quotations.$.productName", product.getString(PRODUCT_NAME))
                .append("quotations.$.dateResponded", new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis())));
    }

    private Document findQuotationAssignedContract() {
        return new Document("quotations.$.responded", true);
    }

    private void updateVendorDocument(Document vendorUpdate, SessionData sessionData, String logo) {
        vendorUpdate.append(ADD_TO_SET_OPERATOR, new Document(CUSTOMERS, new Document("cId", sessionData.getCompanyId())
                .append(COMPANY_ID, sessionData.getCollectionName())
                .append("name", sessionData.getCompanyName())
                .append("type", sessionData.getCustomerType())
                .append("logo", logo)
                .append("email", sessionData.getUserName())));
    }

    private void updateVendorNotifications(SessionData sessionData, String contractId, String vendorCollectionName,
                                           Document product, VendorNotification vendorNotification) {
        Document vendorNotificationDocument = fetchVendorNotificationApprovalDocument(vendorNotification, sessionData, contractId);
        Document searchQuery = new Document("vId", product.getString(VENDOR_ID)).append("role", ADMIN);
        Document updateDocument = new Document(PUSH_OPERATOR, new Document(NOTIFICATIONS, vendorNotificationDocument));
        usersDBDao.updateManyDocument(vendorCollectionName, searchQuery, updateDocument);
        updateNotificationServiceForVendor(vendorCollectionName, vendorNotificationDocument, product);
    }

    private void updateNotificationServiceForVendor(String vendorCollectionName, Document vendorNotificationDocument, Document product) {
        List<String> users = usersDBDao.retrieveVendorAdminIds(vendorCollectionName, product.getString(VENDOR_ID));
        List<String> vendorUsers = new ArrayList<>(users);
        notificationService.pushNotification(vendorNotificationDocument, VENDOR_RECIPIENT, vendorUsers);
    }

    private Document fetchVendorNotificationApprovalDocument(VendorNotification vendorNotification, SessionData sessionData, String contractId) {
        String companyName = sessionData.getCompanyName();
        String userName = sessionData.getUserName();
        String collectionName = sessionData.getCollectionName();
        String companyId = sessionData.getCompanyId();
        vendorNotification.setType(NotificationConstants.NOTIFICATION_TYPE.PRODUCT.value);
        vendorNotification.setTitle("Product Approved");
        vendorNotification.setCreator(userName);
        vendorNotification.setPriority(NotificationConstants.PRIORITY.NORMAL_PRIORITY.value);
        vendorNotification.setActionId(contractId);
        vendorNotification.setSourceName(companyName);
        vendorNotification.setSourceId(companyId);
        vendorNotification.setSourceCollectionId(collectionName);
        vendorNotification.setId(generateNotificationIdWithCompanyId(companyId));
        Document vendorNotificationDocument = Document.parse(new Gson().toJson(vendorNotification));
        vendorNotificationDocument.put(TIME_CREATED, new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()));
        return vendorNotificationDocument;
    }

    private void updateFtlCustomerNotificationDocument(Document customerNotificationDocument, String collectionName, String companyId) {
        Document updateQuery = new Document("cId", companyId).append("role", ADMIN);
        Document documentToBeUpdated = new Document(PUSH_OPERATOR, new Document(NOTIFICATIONS, customerNotificationDocument));
        customersDBDao.updateManyDocuments(collectionName, updateQuery, documentToBeUpdated);
        updateNotificationServiceForCustomer(collectionName, customerNotificationDocument, companyId);
    }

    private void updateNotificationServiceForCustomer(String collectionName, Document customerNotificationDocument, String companyId) {
        List<String> customerUsers = retrieveCustomerUsersList(collectionName, companyId);
        notificationService.pushNotification(customerNotificationDocument, CUSTOMER_RECIPIENT, customerUsers);
    }

    private Document retrieveFtlCustomerNotificationDocument(String userName, Document product, String contractId, String companyId, String userFullName) {
        CustomerNotification customerNotification = new CustomerNotification();
        customerNotification.setType(NotificationConstants.NOTIFICATION_TYPE.PRODUCT.value);
        customerNotification.setCreator(userName);
        customerNotification.setActionId(contractId);
        customerNotification.setTitle(NEW_CONTRACT_ADDED);
        customerNotification.setPriority(NotificationConstants.PRIORITY.NORMAL_PRIORITY.value);
        customerNotification.setText(retrieveContractAdditionText(product, userFullName));
        customerNotification.setId(generateNotificationIdWithCompanyId(companyId));
        customerNotification.setSourceName(product.getString(VENDOR_NAME));
        customerNotification.setSourceId(product.getString(VENDOR_ID));
        customerNotification.setSourceCollectionId(product.getString(VENDOR_COMPANY_ID));
        Document customerNotificationDocument = Document.parse(new Gson().toJson(customerNotification));
        customerNotificationDocument.put(TIME_CREATED, new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()
        ));
        return customerNotificationDocument;
    }

    private String retrieveContractAdditionText(Document product, String userFullName) {
        return product.getString(PRODUCT_NAME) + BY + product.getString(VENDOR_NAME) + HAS_BEEN_ADDED_BY + userFullName;
    }

    private void updateCustomerRepoWithContractDetails(Document product, String vendorCollectionName, SessionData sessionData,
                                                       String vendorId, String contractId, boolean isVendorAlreadyAdded) {
        String companyId = sessionData.getCompanyId();
        String collectionName = sessionData.getCollectionName();
        Document repoUpdate = new Document();
        repoUpdate.append(PULL_OPERATOR, new Document(UNAPPROVED_PRODUCTS, new Document("pId", contractId)));
        product.put("date", new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()));
        repoUpdate.append(PUSH_OPERATOR, new Document(PRODUCTS, product));
        if (!isVendorAlreadyAdded) {
            repoUpdate.append(ADD_TO_SET_OPERATOR,
                    new Document("vendors", fetchVendorDocumentForCustomerContractDetails(vendorId, product, vendorCollectionName)));
            customerRepoDBDao.updateOneDocument(collectionName, new Document("cId", companyId), repoUpdate);
        } else {
            repoUpdate.append(INCREMENT_OPERATOR, new Document("vendors.$.products", 1));
            customerRepoDBDao.updateOneDocument(collectionName, new Document("cId", companyId)
                    .append("vendors.vId", vendorId), repoUpdate);
        }
    }

    private Document fetchVendorDocumentForCustomerContractDetails(String vendorId, Document product, String vendorCollectionName) {
        Document vendorDocument = new Document();
        Vendor vendorObject = sendMyKartDAO.getCompanyDetailsByVendorName(vendorId);
        vendorDocument.put("vId", vendorId);
        vendorDocument.put(COMPANY_ID, vendorCollectionName);
        vendorDocument.put("name", product.getString(VENDOR_NAME));
        vendorDocument.put(PRODUCTS, 1);
        vendorDocument.put("email", vendorObject.getEmail());
        vendorDocument.put("type", vendorObject.getVendorType());
        return vendorDocument;
    }

    private void updatePincodeContractList(String collectionName, ContractApprovalRequest approvalRequest, String vendorCollectionName) {
        List<Document> pincodeList = couriersDBDao.retrieveContractPincodeDetails(vendorCollectionName, approvalRequest.getContractId());
        List<WriteModel<Document>> updatePincodeList = new ArrayList<>();
        for (Document document : pincodeList) {
            Document pincodeQuery = new Document("pincode", document.getString("pincode"));
            Document update = fetchPincodeUpdatedContractDocument(document);
            updatePincodeList.add(new UpdateOneModel<>(pincodeQuery, update));
        }
        if (BasicUtil.isCollectionNotNullOrEmpty(updatePincodeList)) {
            couriersDBDao.bulkWriteIntoCollection(collectionName, updatePincodeList);
        }
    }

    private Document fetchPincodeUpdatedContractDocument(Document document) {
        Document pincodeProductDocument = ((List<Document>) document.get(PRODUCTS)).get(0);
        pincodeProductDocument.put("oda", document.get("oda"));
        pincodeProductDocument.put("region", document.get("region"));
        pincodeProductDocument.put("subRegion", document.get("subRegion"));
        pincodeProductDocument.put("metro", document.get("metro"));
        return new Document(ADD_TO_SET_OPERATOR, new Document(PRODUCTS, pincodeProductDocument));
    }

    private void updateVendorNotificationDocumentForRejectedContractByReason(SessionData sessionData, Document product,
                                                                             String contractId, VendorNotification vendorNotification,
                                                                             String vendorCollectionName) {
        String collectionName = sessionData.getCollectionName();
        String companyId = sessionData.getCompanyId();
        String userName = sessionData.getUserName();
        String companyName = sessionData.getCompanyName();
        vendorNotification.setType(NotificationConstants.NOTIFICATION_TYPE.PRODUCT.value);
        vendorNotification.setCreator(userName);
        vendorNotification.setPriority(NotificationConstants.PRIORITY.HIGH_PRIORITY.value);
        vendorNotification.setActionId(contractId);
        vendorNotification.setSourceName(companyName);
        vendorNotification.setSourceId(companyId);
        vendorNotification.setSourceCollectionId(collectionName);
        vendorNotification.setId(generateNotificationIdWithCompanyId(companyId));
        Document vendorNotificationDocument = Document.parse(new Gson().toJson(vendorNotification));
        vendorNotificationDocument.put(TIME_CREATED,
                new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()));
        usersDBDao.updateManyDocument(vendorCollectionName, new Document("vId",
                product.getString(VENDOR_ID)).append("role", ADMIN), new Document(PUSH_OPERATOR,
                new Document(NOTIFICATIONS, vendorNotificationDocument)));
        updateVendorContractNotificationServiceForRejection(vendorNotificationDocument, vendorCollectionName, product);
    }

    private void updateVendorContractNotificationServiceForRejection(Document vendorNotificationDocument, String vendorCollectionName, Document product) {
        List<String> users = usersDBDao.retrieveVendorAdminIds(vendorCollectionName, product.getString(VENDOR_ID));
        List<String> vendorUsers = new ArrayList<>(users);
        notificationService.pushNotification(vendorNotificationDocument, VENDOR_RECIPIENT, vendorUsers);
    }

    private void updateCustomerNotificationDocumentForRejectedContractByReason(SessionData sessionData, Document product,
                                                                               String contractId, String reason,
                                                                               CustomerNotification customerNotification) {
        String collectionName = sessionData.getCollectionName();
        String companyId = sessionData.getCompanyId();
        String userName = sessionData.getUserName();
        customerNotification.setType(NotificationConstants.NOTIFICATION_TYPE.PRODUCT.value);
        customerNotification.setCreator(userName);
        customerNotification.setActionId(contractId);
        customerNotification.setPriority(NotificationConstants.PRIORITY.NORMAL_PRIORITY.value);
        customerNotification.setText(reason);
        customerNotification.setId(generateNotificationIdWithCompanyId(companyId));
        customerNotification.setSourceName(product.getString(VENDOR_NAME));
        customerNotification.setSourceId(product.getString(VENDOR_ID));
        customerNotification.setSourceCollectionId(product.getString(VENDOR_COMPANY_ID));
        Document customerNotificationDocument = Document.parse(new Gson().toJson(customerNotification));
        customerNotificationDocument.put(TIME_CREATED, new BsonDateTime(TimeUtils.retrieveCurrentTimeInMillis()));
        Document searchQuery = new Document("cId", companyId).append("role", ADMIN);
        Document updateDocument = new Document(PUSH_OPERATOR, new Document(NOTIFICATIONS, customerNotificationDocument));
        customersDBDao.updateManyDocuments(collectionName, searchQuery, updateDocument);
        updateCustomerContractNotificationServiceForRejection(collectionName, customerNotificationDocument, companyId);
    }

    private void updateCustomerContractNotificationServiceForRejection(String collectionName, Document customerNotificationDocument,
                                                                       String companyId) {
        List<String> customerUsers = retrieveCustomerUsersList(collectionName, companyId);
        notificationService.pushNotification(customerNotificationDocument, CUSTOMER_RECIPIENT, customerUsers);
    }

    private String generateNotificationIdWithCompanyId(String companyId) {
        return NOTIFICATION_PREFIX + companyId.toUpperCase() + TimeUtils.retrieveCurrentTimeInMillis();
    }

    private List<String> retrieveCustomerUsersList(String collectionName, String companyId) {
        List<String> customerUsers = new ArrayList<>();
        List<Document> customers = customersDBDao.retrieveCustomerUsersByCompanyId(collectionName, companyId);
        customers.forEach(customer -> customerUsers.add(customer.getString("uId")));
        return customerUsers;
    }

}
