package controller;

import dao.CustomerAddressDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Customer;
import model.CustomerAddress;

@WebServlet("/customer/address-form")
public class CustomerAddressFormController extends HttpServlet {

    private CustomerAddressDAO addressDAO;

    @Override
    public void init() {
        addressDAO = new CustomerAddressDAO();
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        CustomerAddress address = new CustomerAddress();
        String idValue = request.getParameter("id");

        if (idValue != null && !idValue.trim().isEmpty()) {
            Integer addressID = positiveInteger(idValue);

            if (addressID == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            try {
                address = addressDAO.findById(
                        addressID,
                        customer.getCustomerID()
                );

                if (address == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            } catch (SQLException e) {
                getServletContext().log("Could not load customer address.", e);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                );
                return;
            }
        }

        request.setAttribute("address", address);
        request.getRequestDispatcher("/customer/address-form.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        Customer customer = getCustomer(request.getSession(false));

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        CustomerAddress address = new CustomerAddress();
        Integer addressID = positiveInteger(request.getParameter("addressId"));

        if (addressID != null) {
            address.setAddressID(addressID);
        }

        address.setRecipientName(value(request.getParameter("recipientName")));
        address.setPhone(value(request.getParameter("phone")));
        address.setAddressLine(value(request.getParameter("addressLine")));
        address.setWard(value(request.getParameter("ward")));
        address.setDistrict(value(request.getParameter("district")));
        address.setProvince(value(request.getParameter("province")));
        address.setDefaultAddress(
                request.getParameter("isDefault") != null
        );

        String validationError = validate(address);

        if (validationError != null) {
            forwardForm(request, response, address, validationError);
            return;
        }

        try {
            addressDAO.save(address, customer.getCustomerID());

            response.sendRedirect(
                    request.getContextPath() + "/customer/addresses"
            );
        } catch (SQLException e) {
            getServletContext().log("Could not save customer address.", e);
            forwardForm(
                    request,
                    response,
                    address,
                    "Không thể lưu địa chỉ. Vui lòng kiểm tra dữ liệu và thử lại."
            );
        }
    }

    private String validate(CustomerAddress address) {
        if (address.getRecipientName().isEmpty()
                || address.getRecipientName().length() > 300) {
            return "Tên người nhận không hợp lệ.";
        }

        if (!address.getPhone().matches("^[0-9+(). -]{8,20}$")) {
            return "Số điện thoại không hợp lệ.";
        }

        if (address.getAddressLine().isEmpty()
                || address.getAddressLine().length() > 1000) {
            return "Địa chỉ chi tiết không hợp lệ.";
        }

        if (address.getWard().length() > 100
                || address.getDistrict().length() > 100) {
            return "Phường hoặc quận vượt quá độ dài cho phép.";
        }

        if (address.getProvince().isEmpty()
                || address.getProvince().length() > 100) {
            return "Vui lòng nhập tỉnh hoặc thành phố.";
        }

        return null;
    }

    private void forwardForm(HttpServletRequest request,
                             HttpServletResponse response,
                             CustomerAddress address,
                             String error)
            throws ServletException, IOException {

        request.setAttribute("address", address);
        request.setAttribute("error", error);
        request.getRequestDispatcher("/customer/address-form.jsp")
                .forward(request, response);
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }

    private Integer positiveInteger(String value) {
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
