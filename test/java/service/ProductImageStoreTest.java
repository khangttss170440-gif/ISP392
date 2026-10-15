package service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.servlet.http.Part;

/** Run with java service.ProductImageStoreTest; no SQL Server or external library required. */
public class ProductImageStoreTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get("build/verification"), "product-images-");
        ProductImageStore store = new ProductImageStore(root);
        byte[] png = png(40, 60);
        List<ProductImageStore.Upload> uploads = store.readUploads(List.of(new FilePart(png)));
        store.save(12, uploads);
        List<String> names = store.list(12);
        check(names.size() == 1 && names.get(0).startsWith("cover-"), "First photo becomes cover");
        check(names.get(0).endsWith(".png"), "File type comes from decoded content, not submitted name");
        check(ImageIO.read(store.find(12, null).toFile()) != null, "Stored photo decodes successfully");
        store.save(12, uploads);
        check(store.list(12).size() == 2 && store.list(12).get(0).equals(names.get(0)), "Append preserves existing cover");
        check(store.list(13).isEmpty(), "Products use separate folders");
        store.save(13, Collections.emptyList());
        check(Files.isDirectory(root.resolve("13")), "Create folder even without uploads");
        Files.write(root.resolve("12/00-cover.jpg"), png);
        check(store.find(12, null).getFileName().toString().equals("00-cover.jpg"), "Discover manually added cover by filename");
        Files.writeString(root.resolve("12/payload.jsp"), "not an image");
        check(store.list(12).size() == 3, "Ignore unsupported manually placed files");
        check(store.find(12, "../13/photo.png") == null, "Reject directory traversal");
        check(store.find(12, "missing.png") == null, "Missing file is not returned");
        reject(() -> store.list(-1), "Reject invalid product ID");
        reject(() -> store.readUploads(List.of(new FilePart("<script>alert(1)</script>".getBytes()))), "Reject fake image with image MIME");
        reject(() -> store.readUploads(Collections.nCopies(7, new FilePart(png))), "Reject more than six uploads");
        reject(() -> store.readUploads(List.of(new FilePart(new byte[(int) ProductImageStore.MAX_FILE_SIZE + 1]))), "Reject oversized file");
        reject(() -> store.readUploads(List.of(new FilePart(png(3500, 3500)))), "Reject excessive decoded dimensions");
        List<ProductImageStore.Upload> four = store.readUploads(Collections.nCopies(4, new FilePart(png)));
        reject(() -> store.save(12, four), "Reject exceeding total photo limit");
        check(store.list(12).size() == 3, "Rejected upload preserves existing files");
        check(store.readUploads(List.of(new FilePart(new byte[0]))).isEmpty(), "Empty file input is optional");
        store.save(20, store.readUploads(Collections.nCopies(6, new FilePart(png))));
        String selected = store.list(20).get(3);
        store.setImageColor(20, selected, "Đen");
        String secondColor = store.list(20).get(4);
        store.setImageColor(20, secondColor, "Trắng");
        check(new ProductImageStore(root).imageColors(20).get(selected).equals("Đen"), "Vietnamese color persists across instances");
        check(store.findByColor(20, "Đen").getFileName().toString().equals(selected), "Lookup resolves the selected color photo");
        check(store.findByColor(20, " ĐEN ").getFileName().toString().equals(selected), "Color lookup ignores capitalization and extra spaces");
        check(store.findByColor(20, "Trắng").getFileName().toString().equals(secondColor), "Different colors resolve independently");
        store.setCover(20, selected);
        check(new ProductImageStore(root).list(20).get(0).equals(selected), "Cover choice persists across service instances");
        store.replace(20, selected, uploads);
        String replacedCover = store.list(20).get(0);
        check(store.list(20).size() == 6 && !replacedCover.equals(selected), "Replace succeeds at six-image capacity");
        check(store.find(20, selected) == null && store.find(20, replacedCover) != null,
                "Replacement removes old file and keeps new cover");
        check("Đen".equals(store.imageColors(20).get(replacedCover)) && !store.imageColors(20).containsKey(selected),
                "Replacing an image preserves its color mapping");
        reject(() -> store.replace(20, replacedCover, Collections.emptyList()), "Require a replacement image");
        check(store.list(20).size() == 6, "Invalid replacement preserves current files");
        reject(() -> store.delete(20, "../12/00-cover.jpg"), "Delete rejects path traversal");
        reject(() -> store.setCover(20, "../12/00-cover.jpg"), "Cover rejects path traversal");
        store.delete(20, replacedCover);
        check(store.list(20).size() == 5 && store.find(20, null) != null, "Deleting cover selects a remaining image");
        check(!store.imageColors(20).containsKey(replacedCover) && "Trắng".equals(store.imageColors(20).get(secondColor)),
                "Deleting a photo removes only its color mapping");
        store.setImageColor(20, secondColor, "");
        check(!store.imageColors(20).containsKey(secondColor), "Color can be cleared to make a shared photo");
        check(store.findByColor(20, "Không có ảnh").equals(store.find(20, null)), "Unmapped colors fall back to cover");
        reject(() -> store.setImageColor(20, "../12/00-cover.jpg", "Đen"), "Color assignment rejects traversal");
        System.out.println("PASS: product folders, image validation, cover selection, appending, manual discovery, path validation, size/dimension/count limits and optional uploads.");
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Operation { void run() throws Exception; }
    private static void reject(Operation operation, String message) throws Exception {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static final class FilePart implements Part {
        private final byte[] bytes;
        FilePart(byte[] bytes) { this.bytes = bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public String getContentType() { return "image/png"; }
        @Override public String getName() { return "images"; }
        @Override public String getSubmittedFileName() { return "../../danger.jsp"; }
        @Override public long getSize() { return bytes.length; }
        @Override public void write(String fileName) { throw new UnsupportedOperationException(); }
        @Override public void delete() { }
        @Override public String getHeader(String name) { return null; }
        @Override public Collection<String> getHeaders(String name) { return Collections.emptyList(); }
        @Override public Collection<String> getHeaderNames() { return Collections.emptyList(); }
    }
}
