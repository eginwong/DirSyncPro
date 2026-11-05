package dirsyncpro.updater;

import dirsyncpro.Const;
import dirsyncpro.DirSyncPro;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Updater {

  UpdateXmlReader uxmlr;
  boolean inBackground = true;

  public Updater(boolean inB) {
    inBackground = inB;
    String url = Const.UPDATEURL;
    String urlParams = "";
    urlParams += "version=" + URLEncoder.encode(Const.VERSION, StandardCharsets.UTF_8);

    url += "?" + urlParams;

    uxmlr = new UpdateXmlReader(url, inBackground);
  }

  private String readUpdateXML() {
    // for test purposes only

    String updateXML = "";

    try {

      String url = Const.UPDATEURL;
      url += "?version=" + Const.VERSION;
      url += "&os.name=" + System.getProperty("os.name");
      url += "&os.version=" + System.getProperty("os.version");
      url += "&os.arch=" + System.getProperty("os.arch");
      url += "&java.version=" + System.getProperty("java.version");
      url += "&java.vendor=" + System.getProperty("java.vendor");
      url = URLEncoder.encode(url, StandardCharsets.UTF_8);
      URLConnection urlConnection = URI.create(url).toURL().openConnection();

      BufferedReader d = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

      String line;
      while ((line = d.readLine()) != null) {
        updateXML += line + "\n";
      }

      d.close();

    } catch (IllegalArgumentException e) {
      if (!inBackground) DirSyncPro.displayError("Update URL syntax error!");
    } catch (IOException e) {
      if (!inBackground) DirSyncPro.displayError("Unable to contact the update server!");
    }
    return updateXML;
  }

  public boolean updateable() {
    return uxmlr.isUpdateable();
  }

  public void openDownloadURLinBrowser() {
    this.openBrowser(this.getUpdateURL());
  }

  public void openChangelogURLinBrowser() {
    this.openBrowser(this.getChangelogURL());
  }

  private void openBrowser(String url) {
    try {
      Desktop.getDesktop().browse(new URI(url));
    } catch (URISyntaxException e) {
      DirSyncPro.displayError("Update URL syntax error!");
    } catch (IOException e) {
      DirSyncPro.displayError("Unable to start the default internet browser!");
    }
  }

  /**
   * @return <code>String</code> the URL from which the update is to be downloaded.
   */
  public String getUpdateURL() {
    return uxmlr.getUpdateURL();
  }

  /**
   * @return <code>String</code> the new version which is available for download
   */
  public String getNewVersion() {
    return uxmlr.getNewVersion();
  }

  /**
   * @return <code>String</code> the major changes in this update
   */
  public String getChangelogURL() {
    String s = uxmlr.getChangeLogURL();
    return (s.equals("null") ? getUpdateURL() : s);
  }

  /**
   * @return <code>boolean</code> whether there is a new update available
   */
  public boolean isUpdateable() {
    return uxmlr.isUpdateable();
  }

  /**
   * @return <code>boolean</code> if contacting the update server has succeeded.
   */
  public boolean contacted() {
    return uxmlr.contacted();
  }
}
