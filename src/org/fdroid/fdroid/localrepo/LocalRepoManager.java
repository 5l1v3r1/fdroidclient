
package org.fdroid.fdroid.localrepo;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.App;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class LocalRepoManager {
    private static final String TAG = "LocalRepoManager";

    // For ref, official F-droid repo presently uses a maxage of 14 days
    private static final String DEFAULT_REPO_MAX_AGE_DAYS = "14";

    private final PackageManager pm;
    private final AssetManager assetManager;
    private final SharedPreferences prefs;
    private final String fdroidPackageName;

    private String ipAddressString = "UNSET";
    private String uriString = "UNSET";

    private Map<String, App> apps = new HashMap<String, App>();

    public final File xmlIndex;
    public final File webRoot;
    public final File fdroidDir;
    public final File repoDir;
    public final File iconsDir;

    public LocalRepoManager(Context c) {
        pm = c.getPackageManager();
        assetManager = c.getAssets();
        prefs = PreferenceManager.getDefaultSharedPreferences(c);
        fdroidPackageName = c.getPackageName();

        webRoot = c.getFilesDir();
        /* /fdroid/repo is the standard path for user repos */
        fdroidDir = new File(webRoot, "fdroid");
        repoDir = new File(fdroidDir, "repo");
        iconsDir = new File(repoDir, "icons");
        xmlIndex = new File(repoDir, "index.xml");

        if (!fdroidDir.exists())
            if (!fdroidDir.mkdir())
                Log.e(TAG, "Unable to create empty base: " + fdroidDir);

        if (!repoDir.exists())
            if (!repoDir.mkdir())
                Log.e(TAG, "Unable to create empty repo: " + repoDir);

        if (!iconsDir.exists())
            if (!iconsDir.mkdir())
                Log.e(TAG, "Unable to create icons folder: " + iconsDir);
    }

    public void setUriString(String uriString) {
        this.uriString = uriString;
    }

    public void writeIndexPage(String repoAddress) {
        ApplicationInfo appInfo;

        String fdroidClientURL = "https://f-droid.org/FDroid.apk";

        try {
            appInfo = pm.getApplicationInfo(fdroidPackageName, PackageManager.GET_META_DATA);
            File apkFile = new File(appInfo.publicSourceDir);
            File fdroidApkLink = new File(webRoot, "fdroid.client.apk");
            fdroidApkLink.delete();
            if (Utils.symlinkOrCopyFile(apkFile, fdroidApkLink))
                fdroidClientURL = "/" + fdroidApkLink.getName();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        try {
            File indexHtml = new File(webRoot, "index.html");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(assetManager.open("index.template.html"), "UTF-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(indexHtml)));

            String line;
            while ((line = in.readLine()) != null) {
                line = line.replaceAll("\\{\\{REPO_URL\\}\\}", repoAddress);
                line = line.replaceAll("\\{\\{CLIENT_URL\\}\\}", fdroidClientURL);
                out.write(line);
            }
            in.close();
            out.close();
            // make symlinks/copies in each subdir of the repo to make sure that
            // the user will always find the bootstrap page.
            File fdroidDirIndex = new File(fdroidDir, "index.html");
            fdroidDirIndex.delete();
            Utils.symlinkOrCopyFile(indexHtml, fdroidDirIndex);
            File repoDirIndex = new File(repoDir, "index.html");
            repoDirIndex.delete();
            Utils.symlinkOrCopyFile(indexHtml, repoDirIndex);
            // add in /FDROID/REPO to support bad QR Scanner apps
            File fdroidCAPS = new File(fdroidDir.getParentFile(), "FDROID");
            fdroidCAPS.mkdir();
            File repoCAPS = new File(fdroidCAPS, "REPO");
            repoCAPS.mkdir();
            File fdroidCAPSIndex = new File(fdroidCAPS, "index.html");
            fdroidCAPSIndex.delete();
            Utils.symlinkOrCopyFile(indexHtml, fdroidCAPSIndex);
            File repoCAPSIndex = new File(repoCAPS, "index.html");
            repoCAPSIndex.delete();
            Utils.symlinkOrCopyFile(indexHtml, repoCAPSIndex);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteContents(File path) {
        if (path.exists()) {
            for (File file : path.listFiles()) {
                if (file.isDirectory()) {
                    deleteContents(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    public void deleteRepo() {
        deleteContents(repoDir);
    }

    public void copyApksToRepo() {
        copyApksToRepo(new ArrayList<String>(apps.keySet()));
    }

    public void copyApksToRepo(List<String> appsToCopy) {
        for (String packageName : appsToCopy) {
            App app = apps.get(packageName);

            for (Apk apk : app.apks) {
                File outFile = new File(repoDir, apk.apkName);
                if (!Utils.symlinkOrCopyFile(apk.installedFile, outFile)) {
                    throw new IllegalStateException("Unable to copy APK");
                }
            }
        }
    }

    public interface ScanListener {
        public void processedApp(String packageName, int index, int total);
    }

    @TargetApi(9)
    public void addApp(Context context, String packageName) {
        App app;
        try {
            app = new App(context, pm, packageName);
            if (!app.isValid())
                return;
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            app.icon = getIconFile(packageName, packageInfo.versionCode).getName();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return;
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Log.i(TAG, "apps.put: " + packageName);
        apps.put(packageName, app);
    }

    public void removeApp(String packageName) {
        apps.remove(packageName);
    }

    public List<String> getApps() {
        return new ArrayList<String>(apps.keySet());
    }

    public void copyIconsToRepo() {
        for (App app : apps.values()) {
            if (app.apks.size() > 0) {
                Apk apk = app.apks.get(0);
                copyIconToRepo(app.appInfo.loadIcon(pm), app.id, apk.vercode);
            }
        }
    }

    /**
     * Extracts the icon from an APK and writes it to the repo as a PNG
     * 
     * @return path to the PNG file
     */
    public void copyIconToRepo(Drawable drawable, String packageName, int versionCode) {
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        File png = getIconFile(packageName, versionCode);
        OutputStream out;
        try {
            out = new BufferedOutputStream(new FileOutputStream(png));
            bitmap.compress(CompressFormat.PNG, 100, out);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getIconFile(String packageName, int versionCode) {
        return new File(iconsDir, packageName + "_" + versionCode + ".png");
    }

    // TODO this needs to be ported to < android-8
    @TargetApi(8)
    public void writeIndexXML() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.newDocument();
        Element rootElement = doc.createElement("fdroid");
        doc.appendChild(rootElement);

        // max age is an EditTextPreference, which is always a String
        int repoMaxAge = Float.valueOf(prefs.getString("max_repo_age_days",
                DEFAULT_REPO_MAX_AGE_DAYS)).intValue();

        String repoName = prefs.getString("repo_name", Utils.getDefaultRepoName());

        Element repo = doc.createElement("repo");
        repo.setAttribute("icon", "blah.png");
        repo.setAttribute("maxage", String.valueOf(repoMaxAge));
        repo.setAttribute("name", repoName + " on " + ipAddressString);
        long timestamp = System.currentTimeMillis() / 1000L;
        repo.setAttribute("timestamp", String.valueOf(timestamp));
        repo.setAttribute("url", uriString);
        rootElement.appendChild(repo);

        Element repoDesc = doc.createElement("description");
        repoDesc.setTextContent("A repo generated from apps installed on " + repoName);
        repo.appendChild(repoDesc);

        SimpleDateFormat dateToStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (Entry<String, App> entry : apps.entrySet()) {
            String latestVersion = "0";
            String latestVerCode = "0";
            App app = entry.getValue();
            Element application = doc.createElement("application");
            application.setAttribute("id", app.id);
            rootElement.appendChild(application);

            Element appID = doc.createElement("id");
            appID.setTextContent(app.id);
            application.appendChild(appID);

            Element added = doc.createElement("added");
            added.setTextContent(dateToStr.format(app.added));
            application.appendChild(added);

            Element lastUpdated = doc.createElement("lastupdated");
            lastUpdated.setTextContent(dateToStr.format(app.lastUpdated));
            application.appendChild(lastUpdated);

            Element name = doc.createElement("name");
            name.setTextContent(app.name);
            application.appendChild(name);

            Element summary = doc.createElement("summary");
            summary.setTextContent(app.name);
            application.appendChild(summary);

            Element description = doc.createElement("description");
            description.setTextContent(app.name);
            application.appendChild(description);

            Element desc = doc.createElement("desc");
            desc.setTextContent(app.name);
            application.appendChild(desc);

            Element icon = doc.createElement("icon");
            icon.setTextContent(app.icon);
            application.appendChild(icon);

            Element license = doc.createElement("license");
            license.setTextContent("Unknown");
            application.appendChild(license);

            Element categories = doc.createElement("categories");
            categories.setTextContent("LocalRepo," + repoName);
            application.appendChild(categories);

            Element category = doc.createElement("category");
            category.setTextContent("LocalRepo," + repoName);
            application.appendChild(category);

            Element web = doc.createElement("web");
            application.appendChild(web);

            Element source = doc.createElement("source");
            application.appendChild(source);

            Element tracker = doc.createElement("tracker");
            application.appendChild(tracker);

            Element marketVersion = doc.createElement("marketversion");
            application.appendChild(marketVersion);

            Element marketVerCode = doc.createElement("marketvercode");
            application.appendChild(marketVerCode);

            for (Apk apk : app.apks) {
                Element packageNode = doc.createElement("package");

                Element version = doc.createElement("version");
                latestVersion = apk.version;
                version.setTextContent(apk.version);
                packageNode.appendChild(version);

                // F-Droid unfortunately calls versionCode versioncode...
                Element versioncode = doc.createElement("versioncode");
                latestVerCode = String.valueOf(apk.vercode);
                versioncode.setTextContent(latestVerCode);
                packageNode.appendChild(versioncode);

                Element apkname = doc.createElement("apkname");
                apkname.setTextContent(apk.apkName);
                packageNode.appendChild(apkname);

                Element hash = doc.createElement("hash");
                hash.setAttribute("type", apk.hashType);
                hash.setTextContent(apk.hash.toLowerCase(Locale.US));
                packageNode.appendChild(hash);

                Element sig = doc.createElement("sig");
                sig.setTextContent(apk.sig.toLowerCase(Locale.US));
                packageNode.appendChild(sig);

                Element size = doc.createElement("size");
                size.setTextContent(String.valueOf(apk.installedFile.length()));
                packageNode.appendChild(size);

                Element sdkver = doc.createElement("sdkver");
                sdkver.setTextContent(String.valueOf(apk.minSdkVersion));
                packageNode.appendChild(sdkver);

                Element apkAdded = doc.createElement("added");
                apkAdded.setTextContent(dateToStr.format(apk.added));
                packageNode.appendChild(apkAdded);

                Element features = doc.createElement("features");
                if (apk.features != null)
                    features.setTextContent(Utils.CommaSeparatedList.str(apk.features));
                packageNode.appendChild(features);

                Element permissions = doc.createElement("permissions");
                if (apk.permissions != null) {
                    StringBuilder buff = new StringBuilder();

                    for (String permission : apk.permissions) {
                        buff.append(permission.replace("android.permission.", ""));
                        buff.append(",");
                    }
                    String out = buff.toString();
                    if (!TextUtils.isEmpty(out))
                        permissions.setTextContent(out.substring(0, out.length() - 1));
                }
                packageNode.appendChild(permissions);

                application.appendChild(packageNode);
            }

            // now mark the latest version in the feed for this particular app
            marketVersion.setTextContent(latestVersion);
            marketVerCode.setTextContent(latestVerCode);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        DOMSource domSource = new DOMSource(doc);
        StreamResult result = new StreamResult(xmlIndex);

        transformer.transform(domSource, result);
    }
}
