package util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

/** Local Tomcat only: servlet registration, rendered config and upload access control. */
public final class RuntimeConfigurationIntegrationTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory(Path.of("test/.scratch"), "tomcat-config-");
        Path settings = base.resolve("settings.properties");
        Files.writeString(settings, "APP_NAME=Audit & Store\nGHN_SYNC_ENABLED=false\n"
                + "PRODUCT_IMAGE_MAX_FILE_BYTES=2097152\nDB_PASSWORD=PRIVATE_TEST_CANARY\n");
        System.setProperty("MENSFORM_CONFIG_FILE", settings.toAbsolutePath().toString());
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toAbsolutePath().toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context context = tomcat.addWebapp("/audit", Path.of("web").toAbsolutePath().toString());
        StandardRoot resources = new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                Path.of("test/.scratch/config-classes").toAbsolutePath().toString(), "/"));
        context.setResources(resources);
        try {
            tomcat.start();
            require(context.getState().isAvailable(), "Tomcat context started");
            Wrapper images = (Wrapper) context.findChild("productImageManagement");
            Wrapper productForm = (Wrapper) context.findChild("productForm");
            require(images != null && productForm != null, "both upload servlets registered");
            require(images.getMultipartConfigElement().getMaxFileSize() == 2097152L,
                    "image upload servlet uses configured multipart limit");
            require(productForm.getMultipartConfigElement().getMaxFileSize() == 2097152L,
                    "product form uses the same multipart limit");
            int port = tomcat.getConnector().getLocalPort();
            String baseUrl = "http://127.0.0.1:" + port + "/audit";
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login")).build(),
                    HttpResponse.BodyHandlers.ofString());
            require(page.statusCode() == 200, "login JSP renders");
            require(page.body().contains("Audit &amp; Store"), "configured brand is safely rendered");
            require(page.body().contains("data-image-max-bytes=\"2097152\""), "browser receives server upload policy");
            require(!page.body().contains("PRIVATE_TEST_CANARY"), "private settings never reach HTML");
            HttpResponse<String> shell = client.send(HttpRequest.newBuilder(URI.create(baseUrl
                    + "/checkout?addressId=5&promotionCode=MENSFORM10"))
                    .header("Sec-Fetch-Dest", "document").build(), HttpResponse.BodyHandlers.ofString());
            require(shell.statusCode() == 200 && shell.body().contains("data-app-shell"), "top-level navigation uses shell");
            require(shell.body().contains("data-app-root=\"/audit/\""), "shell uses deployed context, not a fixed project path");
            require(shell.body().contains("/audit/checkout?addressId=5&amp;promotionCode=MENSFORM10"),
                    "initial page retains and escapes all checkout parameters");
            require(!shell.body().contains("PRIVATE_TEST_CANARY"), "shell never exposes private configuration");
            HttpResponse<String> home = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                    .header("Sec-Fetch-Dest", "document").build(), HttpResponse.BodyHandlers.ofString());
            require(home.body().contains("data-initial-page=\"/audit/index.jsp\""), "root shell starts on home page");
            HttpResponse<String> inner = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                    .header("Sec-Fetch-Dest", "iframe").build(), HttpResponse.BodyHandlers.ofString());
            require(inner.statusCode() == 200 && !inner.body().contains("data-app-shell"), "frame renders page without nesting shells");
            HttpResponse<String> protectedPage = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/customer/orders"))
                    .header("Sec-Fetch-Dest", "iframe").build(), HttpResponse.BodyHandlers.ofString());
            require(protectedPage.statusCode() == 302 && protectedPage.headers().firstValue("Location").orElse("").endsWith("/login"),
                    "protected frame pages still enforce login");
            HttpResponse<String> submitted = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/checkout"))
                    .header("Sec-Fetch-Dest", "iframe").POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            require(submitted.statusCode() == 302, "form POST still reaches checkout authorization");
            HttpResponse<String> ipn = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/payment/vnpay-ipn"))
                    .header("Accept", "text/html").build(), HttpResponse.BodyHandlers.ofString());
            require(ipn.headers().firstValue("Content-Type").orElse("").contains("application/json")
                    && !ipn.body().contains("data-app-shell"), "provider IPN is never wrapped in HTML");
            HttpResponse<String> upload = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/admin/product-images"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            require(upload.statusCode() == 302 || upload.statusCode() == 403, "upload still requires authorization");
            HttpResponse<String> placeholder = client.send(HttpRequest.newBuilder(
                    URI.create(baseUrl + "/product-image?productId=2147483647")).build(), HttpResponse.BodyHandlers.ofString());
            require(placeholder.statusCode() == 200 && placeholder.body().contains("<svg"), "placeholder asset loads");
            System.out.println("Tomcat runtime configuration integration checks passed: 19");
        } finally {
            tomcat.stop();
            tomcat.destroy();
            Files.deleteIfExists(settings);
        }
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
