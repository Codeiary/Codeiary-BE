package com.codeiary.domain.users.fixture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

public final class ProfileImageFixture {

    private ProfileImageFixture() {
    }

    public static MockMultipartFile jpeg() throws IOException {
        return new MockMultipartFile("profileImage", "profile.jpg", "image/jpeg",
                imageBytes("jpeg", 16, 12));
    }

    public static byte[] imageBytes(String format, int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00ff00);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IOException("Unsupported fixture format: " + format);
        }
        return output.toByteArray();
    }

    public static MockMultipartFile jpegWithComment(String comment) throws IOException {
        byte[] jpeg = imageBytes("jpeg", 16, 12);
        byte[] metadata = comment.getBytes(StandardCharsets.UTF_8);
        var output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(0xff);
        output.write(0xfe);
        output.write((metadata.length + 2) >> 8);
        output.write((metadata.length + 2) & 0xff);
        output.write(metadata);
        output.write(jpeg, 2, jpeg.length - 2);
        return new MockMultipartFile("profileImage", "profile.jpg", "image/jpeg", output.toByteArray());
    }
}
