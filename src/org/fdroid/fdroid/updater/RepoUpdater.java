package org.fdroid.fdroid.updater;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;
import org.fdroid.fdroid.ProgressListener;
import org.fdroid.fdroid.RepoXMLHandler;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.data.RepoProvider;
import org.fdroid.fdroid.net.HttpDownloader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

abstract public class RepoUpdater {

    public static final int PROGRESS_TYPE_DOWNLOAD     = 1;
    public static final int PROGRESS_TYPE_PROCESS_XML  = 2;

    public static RepoUpdater createUpdaterFor(Context ctx, Repo repo) {
        if (repo.fingerprint == null && repo.pubkey == null) {
            return new UnsignedRepoUpdater(ctx, repo);
        } else {
            return new SignedRepoUpdater(ctx, repo);
        }
    }

    protected final Context context;
    protected final Repo repo;
    private List<App> apps = new ArrayList<App>();
    private List<Apk> apks = new ArrayList<Apk>();
    protected boolean usePubkeyInJar = false;
    protected boolean hasChanged = false;
    protected ProgressListener progressListener;

    public RepoUpdater(Context ctx, Repo repo) {
        this.context = ctx;
        this.repo    = repo;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    public List<App> getApps() {
        return apps;
    }

    public List<Apk> getApks() {
        return apks;
    }

    public boolean isInteractive() {
        return progressListener != null;
    }

    /**
     * For example, you may want to unzip a jar file to get the index inside,
     * or if the file is not compressed, you can just return a reference to
     * the downloaded file.
     *
     * @throws UpdateException All error states will come from here.
     */
    protected abstract File getIndexFromFile(File downloadedFile) throws
            UpdateException;

    protected abstract String getIndexAddress();

    protected HttpDownloader downloadIndex() throws UpdateException {
        HttpDownloader downloader = null;
        try {
            downloader = new HttpDownloader(getIndexAddress(), context);
            downloader.setETag(repo.lastetag);

            if (isInteractive()) {
                downloader.setProgressListener(progressListener,
                        new ProgressListener.Event(PROGRESS_TYPE_DOWNLOAD, repo.address));
            }

            int status = downloader.downloadHttpFile();

            if (status == 304) {
                // The index is unchanged since we last read it. We just mark
                // everything that came from this repo as being updated.
                Log.d("FDroid", "Repo index for " + repo.address
                        + " is up to date (by etag)");
            } else if (status == 200) {
                // Nothing needed to be done here...
            } else {
                // Is there any code other than 200 which still returns
                // content? Just in case, lets try to clean up.
                if (downloader.getFile() != null) {
                    downloader.getFile().delete();
                }
                throw new UpdateException(
                        repo,
                        "Failed to update repo " + repo.address +
                        " - HTTP response " + status);
            }
        } catch (SSLHandshakeException e) {
            throw new UpdateException(
                    repo,
                    "A problem occurred while establishing an SSL " +
                    "connection. If this problem persists, AND you have a " +
                    "very old device, you could try using http instead of " +
                    "https for the repo URL.",
                    e );
        } catch (IOException e) {
            if (downloader != null && downloader.getFile() != null) {
                downloader.getFile().delete();
            }
            throw new UpdateException(
                    repo,
                    "Error getting index file from " + repo.address,
                    e);
        }
        return downloader;
    }

    private int estimateAppCount(File indexFile) {
        int count = -1;
        try {
            // A bit of a hack, this might return false positives if an apps description
            // or some other part of the XML file contains this, but it is a pretty good
            // estimate and makes the progress counter more informative.
            // As with asking the server about the size of the index before downloading,
            // this also has a time tradeoff. It takes about three seconds to iterate
            // through the file and count 600 apps on a slow emulator (v17), but if it is
            // taking two minutes to update, the three second wait may be worth it.
            final String APPLICATION = "<application";
            count = Utils.countSubstringOccurrence(indexFile, APPLICATION);
        } catch (IOException e) {
            // Do nothing. Leave count at default -1 value.
        }
        return count;
    }


    public void update() throws UpdateException {

        File downloadedFile = null;
        File indexFile = null;
        try {

            HttpDownloader downloader = downloadIndex();
            hasChanged = downloader.hasChanged();

            if (hasChanged) {

                downloadedFile = downloader.getFile();
                indexFile = getIndexFromFile(downloadedFile);

                // Process the index...
                SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
                XMLReader reader = parser.getXMLReader();
                RepoXMLHandler handler = new RepoXMLHandler(repo, progressListener);

                if (isInteractive()) {
                    // Only bother spending the time to count the expected apps
                    // if we can show that to the user...
                    handler.setTotalAppCount(estimateAppCount(indexFile));
                }

                reader.setContentHandler(handler);
                InputSource is = new InputSource(
                        new BufferedReader(new FileReader(indexFile)));

                reader.parse(is);
                apps = handler.getApps();
                apks = handler.getApks();
                updateRepo(handler, downloader.getETag());
            }
        } catch (SAXException e) {
            throw new UpdateException(
                    repo, "Error parsing index for repo " + repo.address, e);
        } catch (FileNotFoundException e) {
            throw new UpdateException(
                    repo, "Error parsing index for repo " + repo.address, e);
        } catch (ParserConfigurationException e) {
            throw new UpdateException(
                    repo, "Error parsing index for repo " + repo.address, e);
        } catch (IOException e) {
            throw new UpdateException(
                    repo, "Error parsing index for repo " + repo.address, e);
        } finally {
            if (downloadedFile != null &&
                    downloadedFile != indexFile && downloadedFile.exists()) {
                downloadedFile.delete();
            }
            if (indexFile != null && indexFile.exists()) {
                indexFile.delete();
            }

        }
    }

    private void updateRepo(RepoXMLHandler handler, String etag) {

        ContentValues values = new ContentValues();

        values.put(RepoProvider.DataColumns.LAST_UPDATED, Utils.DATE_FORMAT.format(new Date()));

        if (repo.lastetag == null || !repo.lastetag.equals(etag)) {
            values.put(RepoProvider.DataColumns.LAST_ETAG, etag);
        }

        /*
         * We read an unsigned index that indicates that a signed version
         * is available. Or we received a repo config that included the
         * fingerprint, so we need to save the pubkey now.
         */
        if (handler.getPubKey() != null &&
                (repo.pubkey == null || usePubkeyInJar)) {
            // TODO: Spend the time *now* going to get the etag of the signed
            // repo, so that we can prevent downloading it next time. Otherwise
            // next time we update, we have to download the signed index
            // in its entirety, regardless of if it contains the same
            // information as the unsigned one does not...
            Log.d("FDroid",
                    "Public key found - switching to signed repo for future updates");
            values.put(RepoProvider.DataColumns.PUBLIC_KEY, handler.getPubKey());
            usePubkeyInJar = false;
        }

        if (handler.getVersion() != -1 && handler.getVersion() != repo.version) {
            Log.d("FDroid", "Repo specified a new version: from "
                    + repo.version + " to " + handler.getVersion());
            values.put(RepoProvider.DataColumns.VERSION, handler.getVersion());
        }

        if (handler.getMaxAge() != -1 && handler.getMaxAge() != repo.maxage) {
            Log.d("FDroid",
                    "Repo specified a new maximum age - updated");
            values.put(RepoProvider.DataColumns.MAX_AGE, handler.getMaxAge());
        }

        if (handler.getDescription() != null && !handler.getDescription().equals(repo.description)) {
            values.put(RepoProvider.DataColumns.DESCRIPTION, handler.getDescription());
        }

        if (handler.getName() != null && !handler.getName().equals(repo.name)) {
            values.put(RepoProvider.DataColumns.NAME, handler.getName());
        }

        RepoProvider.Helper.update(context, repo, values);
    }

    public static class UpdateException extends Exception {

        private static final long serialVersionUID = -4492452418826132803L;
        public final Repo repo;

        public UpdateException(Repo repo, String message) {
            super(message);
            this.repo = repo;
        }

        public UpdateException(Repo repo, String message, Exception cause) {
            super(message, cause);
            this.repo = repo;
        }
    }

}
