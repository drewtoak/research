/*
    This file is part of the iText (R) project.
    Copyright (c) 1998-2017 iText Group NV
    Authors: iText Software.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.kernel.pdf;

import com.itextpdf.io.font.FontConstants;
import com.itextpdf.kernel.crypto.CryptoUtil;
import com.itextpdf.kernel.PdfException;
import com.itextpdf.kernel.crypto.BadPasswordException;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.filespec.PdfFileSpec;
import com.itextpdf.kernel.utils.CompareTool;
import com.itextpdf.kernel.xmp.XMPConst;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.itextpdf.kernel.xmp.properties.XMPProperty;
import com.itextpdf.test.ExtendedITextTest;
import com.itextpdf.test.ITextTest;
import com.itextpdf.test.annotations.type.IntegrationTest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import static org.junit.Assert.fail;

/**
 * Due to import control restrictions by the governments of a few countries,
 * the encryption libraries shipped by default with the Java SDK restrict the
 * length, and as a result the strength, of encryption keys. Be aware that in
 * this test by using {@link ITextTest#removeCryptographyRestrictions()} we
 * remove cryptography restrictions via reflection for testing purposes.
 * <br/>
 * For more conventional way of solving this problem you need to replace the
 * default security JARs in your Java installation with the Java Cryptography
 * Extension (JCE) Unlimited Strength Jurisdiction Policy Files. These JARs
 * are available for download from http://java.oracle.com/ in eligible countries.
 */
@Category(IntegrationTest.class)
public class PdfEncryptionTest extends ExtendedITextTest {

    /** User password. */
    public static byte[] USER = "Hello".getBytes(StandardCharsets.ISO_8859_1);
    /** Owner password. */
    public static byte[] OWNER = "World".getBytes(StandardCharsets.ISO_8859_1);

    static final String author = "Alexander Chingarev";
    static final String creator = "iText 7";
    static final String pageTextContent = "Hello world!";

    public static final String destinationFolder = "./target/test/com/itextpdf/kernel/pdf/PdfEncryptionTest/";
    public static final String sourceFolder = "./src/test/resources/com/itextpdf/kernel/pdf/PdfEncryptionTest/";

    public static final String CERT = sourceFolder + "test.cer";
    public static final String PRIVATE_KEY = sourceFolder + "test.p12";
    public static final char[] PRIVATE_KEY_PASS = "kspass".toCharArray();
    private PrivateKey privateKey;

    @Rule
    public ExpectedException junitExpectedException = ExpectedException.none();

    @BeforeClass
    public static void beforeClass() {
        createOrClearDestinationFolder(destinationFolder);
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void encryptWithPasswordStandard128() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordStandard128.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_128;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordStandard40() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordStandard40.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_40;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordStandard128NoCompression() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordStandard128NoCompression.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_128;
        encryptWithPassword(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordStandard40NoCompression() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordStandard40NoCompression.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_40;
        encryptWithPassword(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordAes128() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordAes128.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_128;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordAes256() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordAes256.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordAes128NoCompression() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordAes128NoCompression.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_128;
        encryptWithPassword(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithPasswordAes256NoCompression() throws IOException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordAes256NoCompression.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256;
        encryptWithPassword(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateStandard128() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateStandard128.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_128;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateStandard40() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateStandard40.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_40;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateStandard128NoCompression() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateStandard128NoCompression.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_128;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateStandard40NoCompression() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateStandard40NoCompression.pdf";
        int encryptionType = EncryptionConstants.STANDARD_ENCRYPTION_40;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateAes128() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateAes128.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_128;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateAes256() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateAes256.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION);
    }

    @Test
    public void encryptWithCertificateAes128NoCompression() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateAes128NoCompression.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_128;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }


    @Test
    public void encryptWithCertificateAes256NoCompression() throws IOException, XMPException, InterruptedException, GeneralSecurityException {
        String filename = "encryptWithCertificateAes256NoCompression.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256;
        encryptWithCertificate(filename, encryptionType, CompressionConstants.NO_COMPRESSION);
    }

    @Test
    public void openEncryptedDocWithoutPassword() throws IOException {
        junitExpectedException.expect(BadPasswordException.class);
        junitExpectedException.expectMessage(BadPasswordException.BadUserPassword);

        PdfDocument doc = new PdfDocument(new PdfReader(sourceFolder + "encryptedWithPasswordStandard40.pdf"));
        doc.close();
    }

    @Test
    public void openEncryptedDocWithWrongPassword() throws IOException {
        junitExpectedException.expect(BadPasswordException.class);
        junitExpectedException.expectMessage(BadPasswordException.BadUserPassword);

        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithPasswordStandard40.pdf",
                new ReaderProperties().setPassword("wrong_password".getBytes(StandardCharsets.ISO_8859_1)));
        PdfDocument doc = new PdfDocument(reader);
        doc.close();
    }

    @Test
    public void openEncryptedDocWithoutCertificate() throws IOException {
        junitExpectedException.expect(PdfException.class);
        junitExpectedException.expectMessage(PdfException.CertificateIsNotProvidedDocumentIsEncryptedWithPublicKeyCertificate);

        PdfDocument doc = new PdfDocument(new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf"));
        doc.close();
    }

    @Test
    public void openEncryptedDocWithoutPrivateKey() throws IOException, CertificateException {
        junitExpectedException.expect(PdfException.class);
        junitExpectedException.expectMessage(PdfException.BadCertificateAndKey);

        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf",
                new ReaderProperties()
                        .setPublicKeySecurityParams(
                                getPublicCertificate(sourceFolder + "wrong.cer"),
                                null,
                                "BC",
                                null));
        PdfDocument doc = new PdfDocument(reader);
        doc.close();
    }

    @Test
    public void openEncryptedDocWithWrongCertificate() throws IOException, GeneralSecurityException {
        junitExpectedException.expect(PdfException.class);
        junitExpectedException.expectMessage(PdfException.BadCertificateAndKey);

        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf",
                new ReaderProperties()
                        .setPublicKeySecurityParams(
                                getPublicCertificate(sourceFolder + "wrong.cer"),
                                getPrivateKey(),
                                "BC",
                                null));
        PdfDocument doc = new PdfDocument(reader);
        doc.close();
    }

    @Test
    public void openEncryptedDocWithWrongPrivateKey() throws IOException, GeneralSecurityException {
        junitExpectedException.expect(PdfException.class);
        junitExpectedException.expectMessage(PdfException.PdfDecryption);

        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf",
                new ReaderProperties()
                        .setPublicKeySecurityParams(
                                getPublicCertificate(CERT),
                                CryptoUtil.readPrivateKeyFromPKCS12KeyStore(new FileInputStream(sourceFolder + "wrong.p12"), "demo", "password".toCharArray()),
                                "BC",
                                null));
        PdfDocument doc = new PdfDocument(reader);
        doc.close();
    }

    @Test
    public void openEncryptedDocWithWrongCertificateAndPrivateKey() throws IOException, GeneralSecurityException {
        junitExpectedException.expect(PdfException.class);
        junitExpectedException.expectMessage(PdfException.BadCertificateAndKey);

        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf",
                new ReaderProperties()
                        .setPublicKeySecurityParams(
                                getPublicCertificate(sourceFolder + "wrong.cer"),
                                CryptoUtil.readPrivateKeyFromPKCS12KeyStore(new FileInputStream(sourceFolder + "wrong.p12"), "demo", "password".toCharArray()),
                                "BC",
                                null));
        PdfDocument doc = new PdfDocument(reader);
        doc.close();
    }

    @Test
    public void metadataReadingInEncryptedDoc() throws IOException, GeneralSecurityException, XMPException {
        PdfReader reader = new PdfReader(sourceFolder + "encryptedWithPlainMetadata.pdf",
                new ReaderProperties().setPassword(OWNER));
        PdfDocument doc = new PdfDocument(reader);
        XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(doc.getXmpMetadata());
        XMPProperty creatorToolXmp = xmpMeta.getProperty(XMPConst.NS_XMP, "CreatorTool");
        doc.close();
        Assert.assertNotNull(creatorToolXmp);
        Assert.assertEquals("iText 7", creatorToolXmp.getValue());
    }

    @Test
    public void copyEncryptedDocument() throws GeneralSecurityException, IOException, InterruptedException {
        PdfDocument srcDoc = new PdfDocument(new PdfReader(sourceFolder + "encryptedWithCertificateAes128.pdf",
                new ReaderProperties().
                        setPublicKeySecurityParams(getPublicCertificate(CERT), getPrivateKey(), "BC", null)));
        String fileName = "copiedEncryptedDoc.pdf";
        PdfDocument destDoc = new PdfDocument(new PdfWriter(destinationFolder + fileName));
        srcDoc.copyPagesTo(1, 1, destDoc);

        PdfDictionary srcInfo = srcDoc.getDocumentInfo().getPdfObject();
        PdfDictionary destInfo = destDoc.getDocumentInfo().getPdfObject();
        for (PdfName srcInfoKey : srcInfo.keySet()) {
            destInfo.put(srcInfoKey.copyTo(destDoc), srcInfo.get(srcInfoKey).copyTo(destDoc));
        }

        srcDoc.close();
        destDoc.close();

        Assert.assertNull(new CompareTool()
                .compareByContent(destinationFolder + fileName, sourceFolder + "cmp_" + fileName, destinationFolder, "diff_"));
    }

    @Test
    public void openDocNoUserPassword() throws InterruptedException, IOException, XMPException {
        String fileName = "noUserPassword.pdf";
        PdfDocument document = new PdfDocument(new PdfReader(sourceFolder + fileName));
        document.close();

        checkDecryptedWithPasswordContent(sourceFolder + fileName, null, pageTextContent);
    }

    @Test
    public void stampDocNoUserPassword() throws InterruptedException, IOException, XMPException {
        junitExpectedException.expect(BadPasswordException.class);
        junitExpectedException.expectMessage(BadPasswordException.PdfReaderNotOpenedWithOwnerPassword);

        String fileName = "stampedNoPassword.pdf";
        PdfDocument document = new PdfDocument(new PdfReader(sourceFolder + "noUserPassword.pdf"), new PdfWriter(destinationFolder + fileName));
        document.close();
    }

    @Test
    public void encryptWithPasswordAes128EmbeddedFilesOnly() throws IOException, GeneralSecurityException, XMPException, InterruptedException {
        String filename = "encryptWithPasswordAes128EmbeddedFilesOnly.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_128 | EncryptionConstants.EMBEDDED_FILES_ONLY;

        String outFileName = destinationFolder + filename;
        int permissions = EncryptionConstants.ALLOW_SCREENREADERS;
        PdfWriter writer = new PdfWriter(outFileName,
                new WriterProperties().setStandardEncryption(USER, OWNER, permissions, encryptionType).addXmpMetadata()
        );
        PdfDocument document = new PdfDocument(writer);
        document.getDocumentInfo().setAuthor(author).
                setCreator(creator);
        PdfPage page = document.addNewPage();
        String textContent = "Hello world!";
        writeTextBytesOnPageContent(page, textContent);

        String descripton = "encryptedFile";
        String path = sourceFolder + "pageWithContent.pdf";
        document.addFileAttachment(descripton, PdfFileSpec.createEmbeddedFileSpec(document, path, descripton, path, null, null, true));

        page.flush();
        document.close();


        //NOTE: Specific crypto filters for EFF StmF and StrF are not supported at the moment. iText don't distinguish objects based on their semantic role
        //      because of this we can't read streams correctly and corrupt such documents on stamping.
        checkDecryptedWithPasswordContent(destinationFolder + filename, OWNER, textContent, true);
        checkDecryptedWithPasswordContent(destinationFolder + filename, USER, textContent, true);
    }

    @Test
    public void encryptAes256Pdf2NotEncryptMetadata() throws InterruptedException, IOException, XMPException {
        String filename = "encryptAes256Pdf2NotEncryptMetadata.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION, true);
    }

    @Test
    public void encryptAes256EncryptedStampingPreserve() throws InterruptedException, IOException, XMPException {
        String filename = "encryptAes256EncryptedStampingPreserve.pdf";
        String src = sourceFolder + "encryptedWithPlainMetadata.pdf";
        String out = destinationFolder + filename;

        PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(src, new ReaderProperties().setPassword(OWNER)),
                new PdfWriter(out, new WriterProperties()),
                new StampingProperties().preserveEncryption());

        pdfDoc.close();

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();
        String compareResult = compareTool.compareByContent(out, sourceFolder + "cmp_" + filename, destinationFolder, "diff_", USER, USER);
        if (compareResult != null) {
            Assert.fail(compareResult);
        }
    }

    @Test
    public void encryptAes256EncryptedStampingUpdate() throws InterruptedException, IOException, XMPException {
        String filename = "encryptAes256EncryptedStampingUpdate.pdf";
        String src = sourceFolder + "encryptedWithPlainMetadata.pdf";
        String out = destinationFolder + filename;

        PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(src, new ReaderProperties().setPassword(OWNER)),
                new PdfWriter(out, new WriterProperties()
                        .setStandardEncryption(USER, OWNER, EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.STANDARD_ENCRYPTION_40)),
                new StampingProperties());

        pdfDoc.close();

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();
        String compareResult = compareTool.compareByContent(out, sourceFolder + "cmp_" + filename, destinationFolder, "diff_", USER, USER);
        if (compareResult != null) {
            Assert.fail(compareResult);
        }
    }

    @Test
    public void encryptAes256FullCompression() throws InterruptedException, IOException, XMPException {
        String filename = "encryptAes256FullCompression.pdf";
        int encryptionType = EncryptionConstants.ENCRYPTION_AES_256;
        encryptWithPassword(filename, encryptionType, CompressionConstants.DEFAULT_COMPRESSION, true);
    }

    public void encryptWithPassword(String filename, int encryptionType, int compression) throws XMPException, IOException, InterruptedException {
        encryptWithPassword(filename, encryptionType, compression, false);
    }

    public void encryptWithPassword(String filename, int encryptionType, int compression, boolean fullCompression) throws XMPException, IOException, InterruptedException {
        String outFileName = destinationFolder + filename;
        int permissions = EncryptionConstants.ALLOW_SCREENREADERS;
        PdfWriter writer = new PdfWriter(outFileName,
                new WriterProperties()
                        .setStandardEncryption(USER, OWNER, permissions, encryptionType)
                        .addXmpMetadata()
                        .setFullCompressionMode(fullCompression));
        writer.setCompressionLevel(compression);
        PdfDocument document = new PdfDocument(writer);
        document.getDocumentInfo().setAuthor(author).
                setCreator(creator);
        PdfPage page = document.addNewPage();
        writeTextBytesOnPageContent(page, pageTextContent);

        page.flush();
        document.close();

        checkDecryptedWithPasswordContent(destinationFolder + filename, OWNER, pageTextContent);
        checkDecryptedWithPasswordContent(destinationFolder + filename, USER, pageTextContent);

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();
        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_" + filename, destinationFolder, "diff_", USER, USER);
        if (compareResult != null) {
            fail(compareResult);
        }
        checkEncryptedWithPasswordDocumentStamping(filename, OWNER);
        checkEncryptedWithPasswordDocumentAppending(filename, OWNER);
    }

    public void encryptWithCertificate(String filename, int encryptionType, int compression) throws XMPException, IOException, InterruptedException, GeneralSecurityException {
        ITextTest.removeCryptographyRestrictions();

        String outFileName = destinationFolder + filename;
        int permissions = EncryptionConstants.ALLOW_SCREENREADERS;
        Certificate cert = getPublicCertificate(CERT);
        PdfWriter writer = new PdfWriter(outFileName, new WriterProperties()
                .setPublicKeyEncryption(new Certificate[]{cert}, new int[]{permissions}, encryptionType)
                .addXmpMetadata());
        writer.setCompressionLevel(compression);
        PdfDocument document = new PdfDocument(writer);
        document.getDocumentInfo().setAuthor(author).
                setCreator(creator);
        PdfPage page = document.addNewPage();
        writeTextBytesOnPageContent(page, pageTextContent);

        page.flush();
        document.close();

        checkDecryptedWithCertificateContent(filename, cert, pageTextContent);

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();
        compareTool.getOutReaderProperties().setPublicKeySecurityParams(cert, getPrivateKey(), "BC", null);
        compareTool.getCmpReaderProperties().setPublicKeySecurityParams(cert, getPrivateKey(), "BC", null);
        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_" + filename, destinationFolder, "diff_");
        if (compareResult != null) {
            fail(compareResult);
        }

        checkEncryptedWithCertificateDocumentStamping(filename, cert);
        checkEncryptedWithCertificateDocumentAppending(filename, cert);

        ITextTest.restoreCryptographyRestrictions();
    }

    private void writeTextBytesOnPageContent(PdfPage page, String text) throws IOException {
        page.getFirstContentStream().getOutputStream().writeBytes(("q\n" +
                "BT\n" +
                "36 706 Td\n" +
                "0 0 Td\n" +
                "/F1 24 Tf\n" +
                "(" + text + ")Tj\n" +
                "0 0 Td\n" +
                "ET\n" +
                "Q ").getBytes(StandardCharsets.ISO_8859_1));
        page.getResources().addFont(page.getDocument(), PdfFontFactory.createFont(FontConstants.HELVETICA));
    }

    public Certificate getPublicCertificate(String path) throws IOException, CertificateException {
        FileInputStream is = new FileInputStream(path);
        return CryptoUtil.readPublicCertificate(is);
    }

    public PrivateKey getPrivateKey() throws GeneralSecurityException, IOException {
        if (privateKey == null) {
            privateKey = CryptoUtil.readPrivateKeyFromPKCS12KeyStore(new FileInputStream(PRIVATE_KEY), "sandbox", PRIVATE_KEY_PASS);
        }
        return privateKey;
    }

    public void checkDecryptedWithPasswordContent(String src, byte[] password, String pageContent) throws IOException {
        checkDecryptedWithPasswordContent(src, password, pageContent, false);
    }

    private void checkDecryptedWithPasswordContent(String src, byte[] password, String pageContent, boolean expectError) throws IOException {
        PdfReader reader = new com.itextpdf.kernel.pdf.PdfReader(src, new ReaderProperties().setPassword(password));
        PdfDocument document = new com.itextpdf.kernel.pdf.PdfDocument(reader);
        PdfPage page = document.getPage(1);

        if (expectError) {
            Assert.assertFalse("Expected content: \n" + pageContent, new String(page.getStreamBytes(0)).contains(pageContent));
            Assert.assertNotEquals("Encrypted author", author, document.getDocumentInfo().getAuthor());
            Assert.assertNotEquals("Encrypted creator", creator, document.getDocumentInfo().getCreator());
        } else {
            Assert.assertTrue("Expected content: \n" + pageContent, new String(page.getStreamBytes(0)).contains(pageContent));
            Assert.assertEquals("Encrypted author", author, document.getDocumentInfo().getAuthor());
            Assert.assertEquals("Encrypted creator", creator, document.getDocumentInfo().getCreator());
        }

        document.close();
    }

    public void checkDecryptedWithCertificateContent(String filename, Certificate certificate, String pageContent) throws IOException, GeneralSecurityException {
        String src = destinationFolder + filename;
        PdfReader reader = new PdfReader(src, new ReaderProperties()
                .setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null));
        PdfDocument document = new PdfDocument(reader);
        PdfPage page = document.getPage(1);

        String s = new String(page.getStreamBytes(0));
        Assert.assertTrue("Expected content: \n" + pageContent, s.contains(pageContent));
        Assert.assertEquals("Encrypted author", author, document.getDocumentInfo().getAuthor());
        Assert.assertEquals("Encrypted creator", creator, document.getDocumentInfo().getCreator());

        document.close();
    }

    // basically this is comparing content of decrypted by itext document with content of encrypted document
    public void checkEncryptedWithPasswordDocumentStamping(String filename, byte[] password) throws IOException, InterruptedException {
        String srcFileName = destinationFolder + filename;
        String outFileName = destinationFolder + "stamped_" + filename;
        PdfReader reader = new PdfReader(srcFileName, new ReaderProperties().setPassword(password));
        PdfDocument document = new PdfDocument(reader, new PdfWriter(outFileName));
        document.close();

        CompareTool compareTool = new CompareTool();

        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_" + filename, destinationFolder, "diff_", USER, USER);

        if (compareResult != null) {
            fail(compareResult);
        }
    }

    // basically this is comparing content of decrypted by itext document with content of encrypted document
    public void checkEncryptedWithCertificateDocumentStamping(String filename, Certificate certificate) throws IOException, InterruptedException, GeneralSecurityException {
        String srcFileName = destinationFolder + filename;
        String outFileName = destinationFolder + "stamped_" + filename;
        PdfReader reader = new PdfReader(srcFileName, new ReaderProperties()
                .setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null));
        PdfDocument document = new PdfDocument(reader, new PdfWriter(outFileName));
        document.close();

        CompareTool compareTool = new CompareTool();
        compareTool.getCmpReaderProperties().setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null);
        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_" + filename, destinationFolder, "diff_");

        if (compareResult != null) {
            fail(compareResult);
        }
    }

    public void checkEncryptedWithPasswordDocumentAppending(String filename, byte[] password) throws IOException, InterruptedException {
        String srcFileName = destinationFolder + filename;
        String outFileName = destinationFolder + "appended_" + filename;
        PdfReader reader = new PdfReader(srcFileName, new ReaderProperties().setPassword(password));
        PdfDocument document = new PdfDocument(reader, new PdfWriter(outFileName), new StampingProperties().useAppendMode());
        PdfPage newPage = document.addNewPage();
        newPage.put(PdfName.Default, new PdfString("Hello world string"));
        writeTextBytesOnPageContent(newPage, "Hello world page_2!");
        document.close();

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();

        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_appended_" + filename, destinationFolder, "diff_", USER, USER);

        if (compareResult != null) {
            fail(compareResult);
        }
    }

    public void checkEncryptedWithCertificateDocumentAppending(String filename, Certificate certificate) throws IOException, InterruptedException, GeneralSecurityException {
        String srcFileName = destinationFolder + filename;
        String outFileName = destinationFolder + "appended_" + filename;
        PdfReader reader = new PdfReader(srcFileName, new ReaderProperties()
                .setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null));
        PdfDocument document = new PdfDocument(reader, new PdfWriter(outFileName), new StampingProperties().useAppendMode());
        PdfPage newPage = document.addNewPage();
        String helloWorldStringValue = "Hello world string";
        newPage.put(PdfName.Default, new PdfString(helloWorldStringValue));
        writeTextBytesOnPageContent(newPage, "Hello world page_2!");
        document.close();

        PdfReader appendedDocReader = new PdfReader(outFileName, new ReaderProperties()
                .setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null));
        PdfDocument appendedDoc = new PdfDocument(appendedDocReader);
        PdfPage secondPage = appendedDoc.getPage(2);
        PdfString helloWorldPdfString = secondPage.getPdfObject().getAsString(PdfName.Default);
        String actualHelloWorldStringValue = helloWorldPdfString != null ? helloWorldPdfString.getValue() : null;
        Assert.assertEquals(actualHelloWorldStringValue, helloWorldStringValue);
        appendedDoc.close();

        CompareTool compareTool = new CompareTool().enableEncryptionCompare();
        compareTool.getOutReaderProperties().setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null);
        compareTool.getCmpReaderProperties().setPublicKeySecurityParams(certificate, getPrivateKey(), "BC", null);

        String compareResult = compareTool.compareByContent(outFileName, sourceFolder + "cmp_appended_" + filename, destinationFolder, "diff_");

        if (compareResult != null) {
            fail(compareResult);
        }
    }
}
