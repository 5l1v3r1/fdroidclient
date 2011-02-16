/*
 * Copyright (C) 2010  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2009  Roberto Jacinto, roberto.jacinto@caixamagica.pt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.fdroid.fdroid;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.util.Log;

public class RepoXMLHandler extends DefaultHandler {

    String mserver;

    private DB db;

    private DB.App curapp = null;
    private DB.Apk curapk = null;
    private String curchars = null;

    public String pubkey;

    public RepoXMLHandler(String srv, DB db) {
        mserver = srv;
        this.db = db;
        pubkey = null;
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {

        super.characters(ch, start, length);

        String str = new String(ch).substring(start, start + length);
        if (curchars == null)
            curchars = str;
        else
            curchars += str;
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {

        super.endElement(uri, localName, qName);
        String curel = localName;
        String str = curchars;

        if (curel == "application" && curapp != null) {
            // Log.d("FDroid", "Repo: Updating application " + curapp.id);
            db.updateApplication(curapp);
            getIcon(curapp);
            curapp = null;
        } else if (curel == "package" && curapk != null && curapp != null) {
            // Log.d("FDroid", "Repo: Package added (" + curapk.version + ")");
            curapp.apks.add(curapk);
            curapk = null;
        } else if (curapk != null && str != null) {
            if (curel == "version") {
                curapk.version = str;
            } else if (curel == "versioncode") {
                try {
                    curapk.vercode = Integer.parseInt(str);
                } catch (NumberFormatException ex) {
                    curapk.vercode = 0;
                }
            } else if (curel == "size") {
                try {
                    curapk.size = Integer.parseInt(str);
                } catch (NumberFormatException ex) {
                    curapk.size = 0;
                }
            } else if (curel == "hash") {
                curapk.hash = str;
            } else if (curel == "sig") {
                curapk.sig = str;
            } else if (curel == "apkname") {
                curapk.apkName = str;
            } else if (curel == "apksource") {
                curapk.apkSource = str;
            }
        } else if (curapp != null && str != null) {
            if (curel == "id") {
                // Log.d("FDroid", "App id is " + str);
                curapp.id = str;
            } else if (curel == "name") {
                curapp.name = str;
            } else if (curel == "icon") {
                curapp.icon = str;
            } else if (curel == "description") {
                curapp.description = str;
            } else if (curel == "summary") {
                curapp.summary = str;
            } else if (curel == "license") {
                curapp.license = str;
            } else if (curel == "source") {
                curapp.sourceURL = str;
            } else if (curel == "web") {
                curapp.webURL = str;
            } else if (curel == "tracker") {
                curapp.trackerURL = str;
            } else if (curel == "marketversion") {
                curapp.marketVersion = str;
            } else if (curel == "marketvercode") {
                try {
                    curapp.marketVercode = Integer.parseInt(str);
                } catch (NumberFormatException ex) {
                    curapp.marketVercode = 0;
                }
            } else if (curel == "antifeatures") {
                curapp.antiFeatures = str;
            }
        }

    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

        super.startElement(uri, localName, qName, attributes);
        if (localName == "repo") {
            String pk = attributes.getValue("", "pubkey");
            if (pk != null)
                pubkey = pk;
        } else if (localName == "application" && curapp == null) {
            // Log.d("FDroid", "Repo: Found application at " + mserver);
            curapp = new DB.App();
        } else if (localName == "package" && curapp != null && curapk == null) {
            // Log.d("FDroid", "Repo: Found package for " + curapp.id);
            curapk = new DB.Apk();
            curapk.id = curapp.id;
            curapk.server = mserver;
        }
        curchars = null;
    }

    private void getIcon(DB.App app) {
        try {

            String destpath = DB.getIconsPath() + app.icon;
            File f = new File(destpath);
            if (f.exists())
                return;

            BufferedInputStream getit = new BufferedInputStream(new URL(mserver
                    + "/icons/" + app.icon).openStream());
            FileOutputStream saveit = new FileOutputStream(destpath);
            BufferedOutputStream bout = new BufferedOutputStream(saveit, 1024);
            byte data[] = new byte[1024];

            int readed = getit.read(data, 0, 1024);
            while (readed != -1) {
                bout.write(data, 0, readed);
                readed = getit.read(data, 0, 1024);
            }
            bout.close();
            getit.close();
            saveit.close();
        } catch (Exception e) {

        }
    }

    private static void getRemoteFile(Context ctx, String url, String dest)
            throws MalformedURLException, IOException {
        FileOutputStream f = ctx.openFileOutput(dest, Context.MODE_PRIVATE);

        BufferedInputStream getit = new BufferedInputStream(new URL(url)
                .openStream());
        BufferedOutputStream bout = new BufferedOutputStream(f, 1024);
        byte data[] = new byte[1024];

        int readed = getit.read(data, 0, 1024);
        while (readed != -1) {
            bout.write(data, 0, readed);
            readed = getit.read(data, 0, 1024);
        }
        bout.close();
        getit.close();
        f.close();

    }

    public static boolean doUpdates(Context ctx, DB db) {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        db.beginUpdate();
        Vector<DB.Repo> repos = db.getRepos();
        for (DB.Repo repo : repos) {
            if (repo.inuse) {

                try {

                    if (repo.pubkey != null) {

                        // This is a signed repo - we download the jar file,
                        // check the signature, and extract the index...
                        Log.d("FDroid", "Getting signed index from "
                                + repo.address);
                        getRemoteFile(ctx, repo.address + "/index.jar",
                                "tempindex.jar");
                        String jarpath = ctx.getFilesDir() + "/tempindex.jar";
                        JarFile jar = new JarFile(jarpath);
                        JarEntry je = (JarEntry) jar.getEntry("index.xml");
                        File efile = new File(ctx.getFilesDir(),
                                "/tempindex.xml");
                        InputStream in = new BufferedInputStream(jar
                                .getInputStream(je), 8192);
                        OutputStream out = new BufferedOutputStream(
                                new FileOutputStream(efile), 8192);
                        byte[] buffer = new byte[8192];
                        while (true) {
                            int nBytes = in.read(buffer);
                            if (nBytes <= 0)
                                break;
                            out.write(buffer, 0, nBytes);
                        }
                        out.flush();
                        out.close();
                        in.close();
                        java.security.cert.Certificate[] certs = je
                                .getCertificates();
                        jar.close();
                        if (certs == null) {
                            Log.d("FDroid", "No signature found in index");
                            return success = false;
                        }
                        if (certs.length != 1) {
                            Log.d("FDroid", "Expected one signature - found "
                                    + certs.length);
                            return success = false;
                        }

                        byte[] sig = certs[0].getEncoded();
                        byte[] csig = new byte[sig.length * 2];
                        for (int j = 0; j < sig.length; j++) {
                            byte v = sig[j];
                            int d = (v >> 4) & 0xf;
                            csig[j * 2] = (byte) (d >= 10 ? ('a' + d - 10)
                                    : ('0' + d));
                            d = v & 0xf;
                            csig[j * 2 + 1] = (byte) (d >= 10 ? ('a' + d - 10)
                                    : ('0' + d));
                        }
                        String ssig = new String(csig);

                        if (!ssig.equals(repo.pubkey)) {
                            Log.d("FDroid", "Index signature mismatch");
                            return success = false;
                        }

                    } else {

                        // It's an old-fashioned unsigned repo...
                        Log.d("FDroid", "Getting unsigned index from "
                                + repo.address);
                        getRemoteFile(ctx, repo.address + "/index.xml",
                                "tempindex.xml");
                    }

                    // Process the index...
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    SAXParser sp = spf.newSAXParser();
                    XMLReader xr = sp.getXMLReader();
                    RepoXMLHandler handler = new RepoXMLHandler(repo.address,
                            db);
                    xr.setContentHandler(handler);

                    InputStreamReader isr = new FileReader(new File(ctx
                            .getFilesDir()
                            + "/tempindex.xml"));
                    InputSource is = new InputSource(isr);
                    xr.parse(is);

                    if (handler.pubkey != null && repo.pubkey == null) {
                        // We read an unsigned index, but that indicates that
                        // a signed version is now available...
                        Log
                                .d("FDroid",
                                        "Public key found - switching to signed repo for future updates");
                        repo.pubkey = handler.pubkey;
                        db.updateRepoByAddress(repo);
                    }

                } catch (Exception e) {
                    Log.e("FDroid", "Exception updating from " + repo.address
                            + ":\n" + Log.getStackTraceString(e));
                    return success = false;
                } finally {
                    ctx.deleteFile("tempindex.xml");
                    ctx.deleteFile("tempindex.jar");
                    if (!success)
                        db.cancelUpdate();
                }

            }
        }
        db.endUpdate();
        Log.d("FDroid", "Update completed in "
              + ((System.currentTimeMillis() - startTime) / 1000)
              + " seconds.");
        return true;
    }

}
