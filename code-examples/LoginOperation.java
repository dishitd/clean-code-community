package services.loginservice;

import dao.CustomerRepoDBDao;
import dao.SessionsDBDao;
import dao.VendorRepoDBDao;
import exceptions.DataNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import models.CustomerUser;
import models.SMKUser;
import models.SendMyKartDAO;
import models.UserSession;
import models.VendorUser;
import models.responsemodels.LoginResponse;
import org.apache.commons.lang3.StringUtils;
import org.mindrot.jbcrypt.BCrypt;
import play.cache.SyncCacheApi;
import play.data.DynamicForm;
import play.mvc.Http.Cookie;
import util.CookieUtil;
import util.Global;

import static constants.CustomerConstants.USER_TYPE_CLIENT;
import static constants.ErrorMessages.INVALID_CREDENTIALS;
import static constants.GenericConstants.REQUIRED_IMAGE_STRING_LENGTH;
import static constants.MongoDatabase.SMK_COLLECTION;
import static constants.MongoDatabase.SMK_COMPANY_TYPE;
import static constants.SessionCookieName.USER_COMPANY_LOGO_URL;
import static constants.SessionCookieName.USER_COMPONENTS;
import static constants.SessionCookieName.USER_LOGO_URL;
import static constants.SessionDataKeyNames.ADDRESS_ID;
import static constants.SessionDataKeyNames.COLLECTION_NAME;
import static constants.SessionDataKeyNames.COMPANY_ID;
import static constants.SessionDataKeyNames.COMPANY_NAME;
import static constants.SessionDataKeyNames.COMPANY_TYPE;
import static constants.SessionDataKeyNames.CUSTOMER_TYPE;
import static constants.SessionDataKeyNames.CUSTOMER_USER_NAME;
import static constants.SessionDataKeyNames.OPERATION_TYPE_DOMESTIC;
import static constants.SessionDataKeyNames.OPERATION_TYPE_FTL;
import static constants.SessionDataKeyNames.OPERATION_TYPE_SFTL;
import static constants.SessionDataKeyNames.SESSION_ID;
import static constants.SessionDataKeyNames.USER_FULL_NAME;
import static constants.SessionDataKeyNames.USER_ROLE;
import static constants.SessionDataKeyNames.USER_TYPE;
import static constants.SessionDataKeyNames.VENDOR_TYPE;
import static constants.VendorConstants.DOMESTIC_OPERATION;
import static constants.VendorConstants.FTL_CONTRACT_OPERATION;
import static constants.VendorConstants.SHORT_HAUL_FTL_OPERATION;
import static constants.VendorConstants.USER_TYPE_VENDOR;
import static util.UserUtil.isUserCustomer;
import static util.UserUtil.isUserVendor;

public class LoginOperation implements LoginService {

    private final SyncCacheApi cacheApi;
    private final SessionsDBDao sessionsDBDao;
    private final SendMyKartDAO sendMyKartDAO;
    private final VendorRepoDBDao vendorRepoDBDao;
    private final CustomerRepoDBDao customerRepoDBDao;

    @Inject
    public LoginOperation(SyncCacheApi cacheApi, SessionsDBDao sessionsDBDao, SendMyKartDAO sendMyKartDAO, VendorRepoDBDao vendorRepoDBDao, CustomerRepoDBDao customerRepoDBDao) {
        this.cacheApi = cacheApi;
        this.sessionsDBDao = sessionsDBDao;
        this.sendMyKartDAO = sendMyKartDAO;
        this.vendorRepoDBDao = vendorRepoDBDao;
        this.customerRepoDBDao = customerRepoDBDao;
    }

    //TODO: BOTH: error logging
    public LoginResponse retrieveLoginResponse(DynamicForm dynamicForm, String remoteAddress, String collectionName,
                                               String userType) throws IOException {
        String userName = dynamicForm.get("username");
        String password = dynamicForm.get("password");
        String loginId = retrieveLoginIdByUserType(userType, dynamicForm);
        String companyId = retrieveCompanyByUserType(userType, loginId);
        LoginResponse vendorLoginResponse = new LoginResponse();
        UserFactory userFactory = new UserFactory();
        BaseUser userResponse = userFactory.retrieveUserModel(userType, collectionName, loginId, userName);
        String hash = userResponse.retrieveUserHash();
        if (isPasswordCorrect(password, hash)) {
            UserSession userSession = retrieveUserSession(userType, userResponse, collectionName, companyId,
                    userName, remoteAddress);
            createUserSession(userSession);
            Map<String, String> sessionMap = retrieveUserSessionMap(userType, userResponse, userSession,
                    userName, collectionName, companyId);
            List<Cookie> cookieList = retrieveCookieList(userType, userResponse, loginId, companyId, userSession);
            cacheApi.set(userSession.getSessionId(), userSession);
            vendorLoginResponse.setSessionMap(sessionMap);
            vendorLoginResponse.setCookieList(cookieList);
        } else {
            throw new DataNotFoundException(INVALID_CREDENTIALS);
        }
        return vendorLoginResponse;
    }

    private String retrieveLoginIdByUserType(String userType, DynamicForm dynamicForm) {
        return isUserVendor(userType) ? StringUtils.lowerCase(dynamicForm.get("vendorName")) :
                StringUtils.lowerCase(dynamicForm.get("customerName"));
    }

    private String retrieveCompanyByUserType(String userType, String loginId) {
        return isUserVendor(userType) ? sendMyKartDAO.getVendorCompanyIdFromLoginId(loginId) :
                sendMyKartDAO.getCustomerCompanyIdFromLoginId(loginId);
    }

    private boolean isPasswordCorrect(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

    private void createUserSession(UserSession userSession) {
        sessionsDBDao.updateUserSession(userSession);
    }

    public LoginResponse retrieveAdminLoginResponse(DynamicForm dynamicForm, SMKUser user) {
        String userName = dynamicForm.get("username");
        String password = dynamicForm.get("password");
        LoginResponse adminLoginResponse = new LoginResponse();
        if (isPasswordCorrect(password, user.getHash())) {
            adminLoginResponse.setSessionMap(retrieveAdminSessionMap(userName));
        }
        return adminLoginResponse;
    }

    private Map<String, String> retrieveAdminSessionMap(String userName) {
        Map<String, String> sessionMap = new HashMap<>();
        sessionMap.put(COLLECTION_NAME, Global.encrypt(SMK_COLLECTION));
        sessionMap.put(COMPANY_TYPE, Global.encrypt(SMK_COMPANY_TYPE));
        sessionMap.put(COMPANY_ID, Global.encrypt(SMK_COLLECTION));
        sessionMap.put(USER_TYPE, Global.encrypt(SMK_COLLECTION));
        sessionMap.put(CUSTOMER_USER_NAME, Global.encrypt(userName));
        return sessionMap;
    }

    @Override
    public UserSession retrieveUserSession(String userType, BaseUser user, String collectionName, String companyId,
                                           String userName, String remoteAddress) {
        UserSession userSession = null;
        if (isUserVendor(userType)) {
            VendorUser vendorUser = (VendorUser) user;
            userSession = mapToUserSession(collectionName, companyId, userName, remoteAddress);
            updateUserSessionDetails(userSession, vendorUser.getfName() + " " + vendorUser.getlName(),
                    vendorUser.getRole(), vendorUser.getLogoUrl());
        }
        if (isUserCustomer(userType)) {
            CustomerUser customerUser = (CustomerUser) user;
            userSession = mapToUserSession(collectionName, companyId, userName, remoteAddress);
            updateUserSessionDetails(userSession, customerUser.getfName() + " " + customerUser.getlName(),
                    customerUser.getRole(), customerUser.getProfilePic());
        }
        return userSession;
    }

    private void updateUserSessionDetails(UserSession userSession, String fullName, String role, String profilePic) {
        userSession.setName(fullName);
        userSession.setRole(role);
        userSession.setUserImg(profilePic);
    }

    @Override
    public List<Cookie> retrieveCookieList(String userType, BaseUser userResponse, String loginId, String companyId,
                                           UserSession userSession) {
        List<Cookie> cookies = new ArrayList<>();
        if (isUserVendor(userType)) {
            VendorUser vendorUser = (VendorUser) userResponse;
            cookies = vendorUser.retrieveUserCookies(loginId, companyId, userSession);
            updateCookiesWithLogoAndProfileUrl(cookies, vendorUser.getCompanyLogoUrl(), vendorUser.getLogoUrl(),
                    vendorUser.getComponents().toString());
        }
        if (isUserCustomer(userType)) {
            CustomerUser customerUser = (CustomerUser) userResponse;
            cookies = customerUser.retrieveUserCookies(loginId, companyId, userSession);
            updateCookiesWithLogoAndProfileUrl(cookies, customerUser.getLogo(), customerUser.getProfilePic(),
                    customerUser.getComponents().toString());
        }
        return cookies;
    }

    private void updateCookiesWithLogoAndProfileUrl(List<Cookie> cookies, String companyLogoUrl, String logoUrl,
                                                    String components) {
        cookies.add(CookieUtil.getEncryptedCookie(USER_COMPONENTS, components.substring(1), true));
        if (companyLogoUrl.length() < REQUIRED_IMAGE_STRING_LENGTH) {
            cookies.add(CookieUtil.getNonEncryptedCookie(USER_COMPANY_LOGO_URL, companyLogoUrl, false));
        }
        if (logoUrl.length() < REQUIRED_IMAGE_STRING_LENGTH) {
            cookies.add(CookieUtil.getNonEncryptedCookie(USER_LOGO_URL, logoUrl, false));
        }
    }

    private UserSession mapToUserSession(String collectionName, String companyId, String userName, String remoteAddress) {
        return new UserSession.UserSessionBuilder(userName)
                .withCompanyId(companyId)
                .withIpAddress(remoteAddress)
                .withUniqueId(collectionName).build();
    }

    @Override
    public Map<String, String> retrieveUserSessionMap(String userType, BaseUser user, UserSession userSession,
                                                      String userName, String collectionName, String companyId) {
        if (isUserVendor(userType)) {
            return retrieveVendorSessionMap(collectionName, companyId, (VendorUser) user, userSession, userName);
        }
        if (isUserCustomer(userType)) {
            return retrieveCustomerSessionMap(collectionName, companyId, (CustomerUser) user, userSession, userName);
        }
        return null;
    }

    private Map<String, String> retrieveCustomerSessionMap(String collectionName, String companyId, CustomerUser user,
                                                           UserSession userSession, String userName) {
        Map<String, String> sessionMap = retrieveSessionMap(collectionName, companyId, userName, userSession, USER_TYPE_CLIENT);
        String addressId = customerRepoDBDao.retrieveAddressId(collectionName, companyId);
                sessionMap.put(CUSTOMER_TYPE, Global.encrypt(user.getCustomerType()));
        sessionMap.put(USER_FULL_NAME, Global.encrypt(user.getfName() + " " + user.getlName()));
        sessionMap.put(COMPANY_TYPE, Global.encrypt(user.getType()));
        sessionMap.put(USER_ROLE, Global.encrypt(user.getRole()));
        sessionMap.put(COMPANY_NAME, Global.encrypt(user.getCompanyName()));
        sessionMap.put(ADDRESS_ID,Global.encrypt(addressId));
        return sessionMap;
    }

    private Map<String, String> retrieveSessionMap(String collectionName, String companyId, String username,
                                                   UserSession userSession, String userTypeClient) {
        Map<String, String> sessionMap = new HashMap<>();
        sessionMap.put(SESSION_ID, userSession.getSessionId());
        sessionMap.put(COLLECTION_NAME, Global.encrypt(collectionName));
        sessionMap.put(COMPANY_ID, Global.encrypt(companyId));
        sessionMap.put(CUSTOMER_USER_NAME, Global.encrypt(username));
        sessionMap.put(USER_TYPE, Global.encrypt(userTypeClient));
        return sessionMap;
    }

    private Map<String, String> retrieveVendorSessionMap(String collectionName, String companyId, VendorUser user,
                                                         UserSession userSession, String username) {
        String addressId = vendorRepoDBDao.getAddressIdForBranch(collectionName, companyId);
        Map<String, String> sessionMap = retrieveSessionMap(collectionName, companyId, username, userSession, USER_TYPE_VENDOR);
        sessionMap.put(VENDOR_TYPE, Global.encrypt(user.getVendorType()));
        sessionMap.put(ADDRESS_ID, Global.encrypt(addressId));
        sessionMap.put(USER_FULL_NAME, Global.encrypt(user.getfName() + " " + user.getlName()));
        sessionMap.put(COMPANY_TYPE, Global.encrypt(user.getType()));
        sessionMap.put(USER_ROLE, Global.encrypt(user.getRole()));
        sessionMap.put(COMPANY_NAME, Global.encrypt(user.getCompanyName()));
        sessionMap.put(CUSTOMER_TYPE, Global.encrypt(user.getVendorType()));
        if (user.getOperations().contains(DOMESTIC_OPERATION)) {
            sessionMap.put(OPERATION_TYPE_DOMESTIC, Global.encrypt(DOMESTIC_OPERATION));
        }
        if (user.getOperations().contains(SHORT_HAUL_FTL_OPERATION)) {
            sessionMap.put(OPERATION_TYPE_SFTL, Global.encrypt(SHORT_HAUL_FTL_OPERATION));
        }
        if (user.getOperations().contains(FTL_CONTRACT_OPERATION)) {
            sessionMap.put(OPERATION_TYPE_FTL, Global.encrypt(FTL_CONTRACT_OPERATION));
        }
        return sessionMap;
    }

}
