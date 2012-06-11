package com.killerud.skydrive.util;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;

import java.io.File;
import java.io.IOException;
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

    public void findMimeTypeOfFile(String fileName) throws IOException
    {
        File file = new File(fileName);
        findMimeTypeOfFile(file);
    }

    public String findMimeTypeOfFile(File file)
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


}
