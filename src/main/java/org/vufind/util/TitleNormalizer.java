package org.vufind.util;

import java.util.EnumSet;

import org.solrmarc.index.extractor.formatter.FieldFormatter;
import org.solrmarc.tools.DataUtil;

public class TitleNormalizer extends ICUCollatorNormalizer
{
    @Override
    public byte[] normalize(String s)
    {
        EnumSet<FieldFormatter.eCleanVal> cleanValue = DataUtil.getCleanValForParam("titleSortLower");
        String normalizedTitle = DataUtil.cleanByVal(s, cleanValue);
        if (normalizedTitle == null) {
            return null;
        }
        return collator.getCollationKey(normalizedTitle).toByteArray();
    }
}
