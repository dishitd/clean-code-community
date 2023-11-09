public class Bill {
    public Result billTable(Http.Request request) throws NoSuchAlgorithmException {
        ArrayList<ArrayList> list = new ArrayList<>();
        try {
            String collectionName = Global.decrypt(request.session().getOptional("col").orElse(null));
            List<Document> query = billsDBDao.clientBillQueryGenerator(formFactory.form().bindFromRequest(request));
            if (query != null) {
                AggregateIterable<Document> iterable = customerRepoDb.getCollection(collectionName).aggregate(query);
                for (Document document : iterable) {
                    document.putIfAbsent("product", "");
                    document.putIfAbsent("pId", "");
                    ArrayList<String> data = new ArrayList<>();
                    data.add(document.getString("id"));
                    data.add(PaymentStatus.fromId(document.getInteger("status")));
                    data.add(document.getString("vendorName").toUpperCase());
                    data.add(document.getString("product").toUpperCase());
                    data.add(new DateTime(document.getDate("generatedTime")).toString("dd-MM-YYYY"));
                    data.add(new DateTime(document.getDate("interestTime")).toString("dd-MM-YYYY"));
                    data.add(Operations.roundingToTwoDecimals(document.getDouble("totalAmount")) + "");
                    data.add(Operations.roundingToTwoDecimals(document.getDouble("totalAmountPaid")) + "");
                    data.add(Operations.roundingToTwoDecimals(document.getDouble("totalAmountRemaining")) + "");
                    data.add(document.getString("vId").toUpperCase());
                    data.add(document.getString("contractId"));
                    data.add(document.getString("cId").toLowerCase());
                    if ((document.getString("contractId")).contains("FTL")) {
                        data.add("FTL");
                    } else {
                        data.add("LTL");
                    }

                    list.add(data);

                }
                DataTableModel model = new DataTableModel();
                model.setData(list);
                return ok(Json.toJson(model)).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return badRequest().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    }
}
