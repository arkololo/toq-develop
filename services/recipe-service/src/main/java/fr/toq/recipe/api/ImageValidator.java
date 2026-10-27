package fr.toq.recipe.api;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class ImageValidator {
  private ImageValidator() {}
  static ImageData read(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 3_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image vide ou trop volumineuse (3 Mo maximum).");
    try {
      return readBytes(file.getBytes());
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image invalide."); }
  }
  static ImageData readBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > 3_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image vide ou trop volumineuse (3 Mo maximum).");
    try {
      try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image invalide.");
        ImageReader reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
          if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seuls PNG et JPEG sont acceptés.");
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > 3000 || height > 3000 || (long) width * height > 9_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensions de l'image invalides.");
          return new ImageData(format.equals("png") ? "image/png" : "image/jpeg", bytes);
        } finally { reader.dispose(); }
      }
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image invalide."); }
  }
  record ImageData(String mimeType, byte[] bytes) {}
}
