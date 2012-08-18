package com.killerud.skydrive.util;

import com.killerud.skydrive.R;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Iterator;

/**
 * User: William
 * Date: 07.05.12
 * Time: 15:23
 */
public class IOUtil
{
    public IOUtil()
    {

    }

    public static String toHex(String arg) {
        return String.format("%040x", new BigInteger(arg.getBytes(/*YOUR_CHARSET?*/)));
    }

    public static void findMimeTypeOfFile(String fileName) throws IOException
    {
        File file = new File(fileName);
        findMimeTypeOfFile(file);
    }



    public static String findMimeTypeOfFile(File file)
    {
        MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
        Collection mimeTypes = MimeUtil.getMimeTypes(file);

        if (mimeTypes.isEmpty())
        {
            /* Unknown file type, so here we just give Jesus the wheel and open it as a plain text file */
            return "text/plain";
        }
        else
        {
            Iterator iterator = mimeTypes.iterator();
            MimeType mimeType = (MimeType) iterator.next();
            String mimetype = mimeType.getMediaType() + "/" + mimeType.getSubType();
            return mimetype;
        }
    }


    public static int determineFileTypeDrawable(String fileExtension)
    {
        if (fileExtension.equalsIgnoreCase("png") ||
                fileExtension.equalsIgnoreCase("jpg") ||
                fileExtension.equalsIgnoreCase("jpeg") ||
                fileExtension.equalsIgnoreCase("tiff") ||
                fileExtension.equalsIgnoreCase("gif") ||
                fileExtension.equalsIgnoreCase("bmp") ||
                fileExtension.equalsIgnoreCase("raw"))
        {
            return R.drawable.image_x_generic;
        }
        else if (fileExtension.equalsIgnoreCase("mp3") ||
                fileExtension.equalsIgnoreCase("wav") ||
                fileExtension.equalsIgnoreCase("wma") ||
                fileExtension.equalsIgnoreCase("acc") ||
                fileExtension.equalsIgnoreCase("ogg"))
        {
            return R.drawable.audio_x_generic;
        }
        else if (fileExtension.equalsIgnoreCase("mov") ||
                fileExtension.equalsIgnoreCase("avi") ||
                fileExtension.equalsIgnoreCase("divx") ||
                fileExtension.equalsIgnoreCase("wmv") ||
                fileExtension.equalsIgnoreCase("ogv") ||
                fileExtension.equalsIgnoreCase("mkv") ||
                fileExtension.equalsIgnoreCase("mp4"))
        {
            return R.drawable.video_x_generic;
        }
        else if (fileExtension.equalsIgnoreCase("doc") ||
                fileExtension.equalsIgnoreCase("odt") ||
                fileExtension.equalsIgnoreCase("fodt") ||
                fileExtension.equalsIgnoreCase("docx") ||
                fileExtension.equalsIgnoreCase("odf"))
        {
            return R.drawable.office_document;
        }
        else if (fileExtension.equalsIgnoreCase("ppt") ||
                fileExtension.equalsIgnoreCase("pps") ||
                fileExtension.equalsIgnoreCase("pptx") ||
                fileExtension.equalsIgnoreCase("ppsx") ||
                fileExtension.equalsIgnoreCase("odp") ||
                fileExtension.equalsIgnoreCase("fodp"))
        {
            return R.drawable.office_presentation;
        }
        else if (fileExtension.equalsIgnoreCase("ods") ||
                fileExtension.equalsIgnoreCase("xls") ||
                fileExtension.equalsIgnoreCase("xlr") ||
                fileExtension.equalsIgnoreCase("xlsx") ||
                fileExtension.equalsIgnoreCase("ots"))
        {
            return R.drawable.office_spreadsheet;
        }
        else if (fileExtension.equalsIgnoreCase("pdf"))
        {
            return R.drawable.document_pdf;
        }
        else if (fileExtension.equalsIgnoreCase("zip") ||
                fileExtension.equalsIgnoreCase("rar") ||
                fileExtension.equalsIgnoreCase("gz") ||
                fileExtension.equalsIgnoreCase("bz2") ||
                fileExtension.equalsIgnoreCase("tar") ||
                fileExtension.equalsIgnoreCase("jar"))
        {
            return R.drawable.archive_generic;
        }
        else if (fileExtension.equalsIgnoreCase("7z"))
        {
            return R.drawable.archive_sevenzip;
        }
        else if (fileExtension.equalsIgnoreCase("torrent"))
        {
            return R.drawable.document_torrent;
        }
        else if (fileExtension.equalsIgnoreCase("exe") ||
                fileExtension.equalsIgnoreCase("msi"))
        {
            return R.drawable.executable_generic;
        }
        else if (fileExtension.equalsIgnoreCase("iso") ||
                fileExtension.equalsIgnoreCase("nrg") ||
                fileExtension.equalsIgnoreCase("img") ||
                fileExtension.equalsIgnoreCase("bin"))
        {
            return R.drawable.archive_disc_image;
        }
        else if (fileExtension.equalsIgnoreCase("apk"))
        {
            return R.drawable.executable_apk;
        }
        else if (fileExtension.equalsIgnoreCase("html") ||
                fileExtension.equalsIgnoreCase("htm"))
        {
            return R.drawable.text_html;
        }
        else if (fileExtension.equalsIgnoreCase("css"))
        {
            return R.drawable.text_css;
        }
        else if (fileExtension.equalsIgnoreCase("deb"))
        {
            return R.drawable.executable_deb;
        }
        else if (fileExtension.equalsIgnoreCase("rpm"))
        {
            return R.drawable.executable_rpm;
        }
        else if (fileExtension.equalsIgnoreCase("java") ||
                fileExtension.equalsIgnoreCase("class"))
        {
            return R.drawable.document_java;
        }
        else if (fileExtension.equalsIgnoreCase("pl") ||
                fileExtension.equalsIgnoreCase("plc"))
        {
            return R.drawable.document_perl;
        }
        else if (fileExtension.equalsIgnoreCase("php"))
        {
            return R.drawable.document_php;
        }
        else if (fileExtension.equalsIgnoreCase("py"))
        {
            return R.drawable.document_python;
        }
        else if (fileExtension.equalsIgnoreCase("rb"))
        {
            return R.drawable.document_ruby;
        }
        return R.drawable.text_x_preview;
    }

    public static String getFileExtension(File file)
    {
        String fileName = file.getName();
        String extension = "";
        int positionOfLastDot = fileName.lastIndexOf(".");
        extension = fileName.substring(positionOfLastDot + 1, fileName.length());
        return extension;
    }

    public static String getFileExtension(String file)
    {
        String fileName = file;
        String extension = "";
        int positionOfLastDot = fileName.lastIndexOf(".");
        extension = fileName.substring(positionOfLastDot + 1, fileName.length());
        return extension;
    }
}
