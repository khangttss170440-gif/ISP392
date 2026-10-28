package util;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Release application-owned connections and driver registrations on redeploy. */
@WebListener
public class DatabaseLifecycleListener implements ServletContextListener {
    private service.GhnSyncScheduler shippingSync;
    @Override public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setSessionTimeout((int) Math.ceil(AppConfig.integer("SESSION_TIMEOUT_SECONDS", 60, 86400) / 60.0));
        javax.servlet.ServletRegistration.Dynamic imageServlet = event.getServletContext()
                .addServlet("productImageManagement", controller.ProductImageManagementController.class);
        imageServlet.addMapping("/staff/warehouse/product-images", "/admin/product-images");
        imageServlet.setMultipartConfig(new javax.servlet.MultipartConfigElement("",
                service.ProductImageStore.MAX_FILE_SIZE, service.ProductImageStore.MAX_REQUEST_SIZE,
                AppConfig.integer("PRODUCT_IMAGE_MEMORY_THRESHOLD_BYTES", 0, Integer.MAX_VALUE)));
        javax.servlet.ServletRegistration.Dynamic productForm = event.getServletContext()
                .addServlet("productForm", controller.AdminProductFormController.class);
        productForm.addMapping("/admin/product-form", "/staff/warehouse/product-form");
        productForm.setMultipartConfig(new javax.servlet.MultipartConfigElement("",
                service.ProductImageStore.MAX_FILE_SIZE, service.ProductImageStore.MAX_REQUEST_SIZE,
                AppConfig.integer("PRODUCT_IMAGE_MEMORY_THRESHOLD_BYTES", 0, Integer.MAX_VALUE)));
        if (AppConfig.bool("GHN_SYNC_ENABLED")) {
            shippingSync=new service.GhnSyncScheduler(event.getServletContext());
            shippingSync.start();
        }
    }
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if(shippingSync!=null) shippingSync.close();
        try {
            // Local DBConnection may own a pool; older/private replacements expose only getConnection().
            DBConnection.class.getMethod("shutdown").invoke(null);
        } catch (NoSuchMethodException noPool) {
            // DriverManager-based DBConnection has no application-owned pool to close.
        } catch (ReflectiveOperationException error) {
            event.getServletContext().log("Could not close the database pool.", error);
        }
        ClassLoader applicationLoader = DatabaseLifecycleListener.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == applicationLoader) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException error) {
                    event.getServletContext().log("Could not unregister the database driver.", error);
                }
            }
        }
    }
}
