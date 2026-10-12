package com.example.camel.processor;

import jakarta.activation.DataHandler;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Used by API-8: reads the uploaded file and assembles the reply.
 *
 * <p>By the time the route runs, {@code Base64FileMultipartFilter} has already decoded the Base64
 * {@code file} field into a binary file part, and platform-http has turned that part into a
 * Camel <b>attachment</b> named after the form field. That is how a Processor gets a multipart file:
 * <pre>
 * AttachmentMessage am = exchange.getIn(AttachmentMessage.class);
 * DataHandler file = am.getAttachment("file");
 * </pre>
 * For a request with exactly one file, platform-http also sets the body to the file's temporary
 * {@code Path} and fills {@code CamelFileName} / {@code CamelFileContentType} / {@code CamelFileLength};
 * the temporary file is deleted when the exchange completes. Plain form fields arrive as headers.
 */
@Component("fileUploadProcessor")
public class FileUploadProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(FileUploadProcessor.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PREVIEW_CHARS = 64;

    @Override
    public void process(Exchange exchange) throws Exception {
        DataHandler attachment = exchange.getIn(AttachmentMessage.class).getAttachment("file");
        if (attachment == null) {
            throw new IllegalArgumentException("multipart field 'file' is required");
        }
        byte[] bytes;
        try (InputStream in = attachment.getInputStream()) {
            bytes = in.readAllBytes();
        }
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        String contentType = exchange.getIn().getHeader(Exchange.FILE_CONTENT_TYPE,
                attachment.getContentType(), String.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", "OK");
        out.put("orderId", exchange.getIn().getHeader("orderId"));
        out.put("fileName", fileName);
        out.put("contentType", contentType);
        out.put("size", bytes.length);
        out.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        if (contentType.startsWith("text/")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            out.put("preview", text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) + "..." : text);
        }
        out.put("exchangeId", exchange.getExchangeId());
        out.put("processedAt", LocalDateTime.now().format(FMT));

        exchange.getIn().setBody(out);
        log.info("[API-8][process] got file {} ({}, {} bytes) from attachment 'file'",
                fileName, contentType, bytes.length);
    }
}
