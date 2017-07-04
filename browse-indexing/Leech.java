import org.apache.lucene.store.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;


import org.vufind.util.BrowseEntry;
import org.vufind.util.Normalizer;
import org.vufind.util.NormalizerFactory;


class MyLog
{
    private static Logger log ()
    {
        // Caller's class
        return Logger.getLogger
            (new Throwable ().getStackTrace ()[2].getClassName ());
    }


    public static String formatBytes(byte[] bytes) {
        StringBuilder result = new StringBuilder ();

        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                result.append (", ");
            }
            result.append ("0x");
            result.append (Integer.toHexString (bytes[i]));
        }

        return result.toString();
    }


    public static String formatBytes(String s) {
        try {
            return formatBytes(s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    public static void info (String s) { log ().info (s); }

    public static void info (String fmt, String s) {
        log ().info (String.format (fmt, s));
    }
}


public class Leech
{
    protected CompositeReader reader;
    protected IndexSearcher searcher;
    protected String filter;

    protected List<LeafReaderContext> leafReaders;

    private String field;
    private Normalizer normalizer;

    TermsEnum tenum = null;


    public Leech (String indexPath,
                  String field, String filter) throws Exception
    {
        // Open our composite reader (a top-level DirectoryReader that
        // contains one reader per segment in our index).
        reader = DirectoryReader.open (FSDirectory.open (new File (indexPath).toPath ()));

        // Open the searcher that we'll use to verify that items are
        // being used by a non-deleted document.
        searcher = new IndexSearcher (reader);

        // Extract the list of readers for our underlying segments.
        // We'll work through these one at a time until we've consumed them all.
        leafReaders = new ArrayList<>(reader.getContext().leaves());

        this.field = field;
        this.filter = filter;

        String normalizerClass = System.getProperty("browse.normalizer");
        normalizer = NormalizerFactory.getNormalizer(normalizerClass);
    }


    public byte[] buildSortKey (String heading)
    {
        return normalizer.normalize (heading);
    }


    public void dropOff () throws IOException
    {
        reader.close ();
    }


    private boolean termExists (String t)
    {
        try {
            Query q;
            if (this.filter != null) {
                TermQuery tq = new TermQuery (new Term (this.field, t));
                TermQuery fq = new TermQuery (new Term (this.filter, "T"));
                BooleanQuery.Builder qb = new BooleanQuery.Builder();
                qb.add(tq, BooleanClause.Occur.MUST);
                qb.add(fq, BooleanClause.Occur.MUST);
                q = qb.build();
            }
            else {
                q = new TermQuery (new Term (this.field, t));
            }

MyLog.info("WE ARE IN TERM EXISTS:" + q.toString());
               
            return (this.searcher.search (new ConstantScoreQuery(q),
                                          1).totalHits > 0);
        } catch (IOException e) {
            return false;
        }
    }


    // Return the next term from the currently selected TermEnum, if there is one.  Null otherwise.
    //
    // If there's no currently selected TermEnum, create one from the reader.
    //
    public BrowseEntry next () throws Exception
    {
        if (tenum == null) {
            if (leafReaders.isEmpty()) {
                // Nothing left to do
                return null;
            }

            // Select our next LeafReader to work from
            LeafReader ir = leafReaders.remove(0).reader();
            Terms terms = ir.terms(this.field);

            if (terms == null) {
                // Try the next reader
                return next();
            }

            tenum = terms.iterator();
        }

        if (tenum.next() != null) {
            String termText = tenum.term().utf8ToString();

            if (termExists(termText)) {
MyLog.info("Term exists....");
                return new BrowseEntry (buildSortKey (termText), termText, termText) ;
            } else {
MyLog.info("Term does not exist");
                return this.next();
            }
        } else {
            // Exhausted this reader
            tenum = null;
            return null;
        }
    }
}
