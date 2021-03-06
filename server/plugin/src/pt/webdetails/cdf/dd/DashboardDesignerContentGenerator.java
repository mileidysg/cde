package pt.webdetails.cdf.dd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.servlet.http.HttpServletResponse;


import net.sf.json.JSON;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.pentaho.platform.api.engine.IContentGenerator;
import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IPluginManager;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.api.engine.ObjectFactoryException;
import org.pentaho.platform.api.repository.IContentItem;
import org.pentaho.platform.api.repository.ISolutionRepository;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.services.solution.BaseContentGenerator;
import org.pentaho.platform.engine.services.solution.SolutionReposHelper;


import pt.webdetails.cdf.dd.olap.OlapUtils;
import pt.webdetails.cdf.dd.render.DependenciesManager;
import pt.webdetails.cdf.dd.render.components.ComponentManager;
import pt.webdetails.cdf.dd.util.JsonUtils;
import pt.webdetails.cdf.dd.util.Utils;

import pt.webdetails.cdf.dd.packager.Packager;
import pt.webdetails.cpf.PluginUtils;

@SuppressWarnings("unchecked")
public class DashboardDesignerContentGenerator extends BaseContentGenerator
{

  public static final String PLUGIN_NAME = "pentaho-cdf-dd";
  public static final String PLUGIN_PATH = "system/" + DashboardDesignerContentGenerator.PLUGIN_NAME + "/";
  /**
   * solution folder for custom components, styles and templates
   */
  public static final String SOLUTION_DIR = "cde";
  public static final String SERVER_URL_VALUE = Utils.getBaseUrl() + "content/pentaho-cdf-dd/";
  private static Log logger = LogFactory.getLog(DashboardDesignerContentGenerator.class);
  private static final long serialVersionUID = 1L;
  private static final String MIME_TYPE = "text/html";
  private static final String CSS_TYPE = "text/css";
  private static final String JAVASCRIPT_TYPE = "text/javascript";
  private static final String FILE_NAME_TAG = "@FILENAME@";
  private static final String SERVER_URL_TAG = "@SERVERURL@";
  private static final String DESIGNER_RESOURCE = "resources/cdf-dd.html";
  private static final String DESIGNER_STYLES_RESOURCE = "resources/styles.html";
  private static final String DESIGNER_SCRIPTS_RESOURCE = "resources/scripts.html";
  private static final String DESIGNER_HEADER_TAG = "@HEADER@";
  private static final String DESIGNER_STYLES_TAG = "@STYLES@";
  private static final String DESIGNER_SCRIPTS_TAG = "@SCRIPTS@";
  private static final String DATA_URL_TAG = "cdf-structure.js";
  private static final String DATA_URL_VALUE = Utils.getBaseUrl() + "content/pentaho-cdf-dd/Syncronize";
  private static final String ENCODING = "UTF-8";
  /**
   * 1 week cache
   */
  private static final int RESOURCE_CACHE_DURATION = 3600 * 24 * 8; //1 week cache
  private static final int NO_CACHE_DURATION = 0;
  private static final String EXTERNAL_EDITOR_PAGE = "resources/ext-editor.html";
//  private static final String[] ALLOWED_SOLUTION_DIRS = {SOLUTION_DIR, Utils.joinPath("system", PLUGIN_NAME, "resource")};
  private Packager packager;

  public enum FileTypes
  {

    JPG, JPEG, PNG, GIF, BMP, JS, CSS, HTML, HTM, XML
  }
  public static final EnumMap<FileTypes, String> mimeTypes = new EnumMap<FileTypes, String>(FileTypes.class);

  /**
   * Path parameters received by content generator
   */
  protected static class PathParams
  {

    /**
     * Debug flag
     */
    public static final String DEBUG = "debug";
    public static final String ROOT = "root";
    public static final String SOLUTION = "solution";
    public static final String PATH = "path";
    public static final String FILE = "file";
    /**
     * JSON structure
     */
    public static final String CDF_STRUCTURE = "cdfstructure";
    public static final String DATA = "data";
  }

  static
  {
    /*
     * Image types
     */
    mimeTypes.put(FileTypes.JPG, "image/jpeg");
    mimeTypes.put(FileTypes.JPEG, "image/jpeg");
    mimeTypes.put(FileTypes.PNG, "image/png");
    mimeTypes.put(FileTypes.GIF, "image/gif");
    mimeTypes.put(FileTypes.BMP, "image/bmp");

    /*
     * HTML (and related) types
     */
    // Deprecated, should be application/javascript, but IE doesn't like that
    mimeTypes.put(FileTypes.JS, "text/javascript");
    mimeTypes.put(FileTypes.HTM, "text/html");
    mimeTypes.put(FileTypes.HTML, "text/html");
    mimeTypes.put(FileTypes.CSS, "text/css");
    mimeTypes.put(FileTypes.XML, "text/xml");
  }

  public DashboardDesignerContentGenerator()
  {
    try
    {
      init();
    }
    catch (IOException ex)
    {
      logger.error("Failed to initialize!");
    }
  }

  @Override
  public void createContent() throws Exception
  {

    // Make sure we have the env. correctly inited
    if (SolutionReposHelper.getSolutionRepositoryThreadVariable() == null && PentahoSystem.getObjectFactory().objectDefined(ISolutionRepository.class.getSimpleName()))
    {
      SolutionReposHelper.setSolutionRepositoryThreadVariable(PentahoSystem.get(ISolutionRepository.class, PentahoSessionHolder.getSession()));
    }


    final IParameterProvider pathParams = parameterProviders.get("path");
    final IParameterProvider requestParams = parameterProviders.get("request");

    final IContentItem contentItem = outputHandler.getOutputContentItem("response", "content", "", instanceId, MIME_TYPE);

    final OutputStream out = contentItem.getOutputStream(null);

    try
    {

      final Class[] params =
      {
        IParameterProvider.class, OutputStream.class
      };

      final String method = pathParams.getStringParameter(PathParams.PATH, null).split("/")[1].toLowerCase();

      try
      {
        final Method mthd = this.getClass().getMethod(method, params);
        mthd.invoke(this, requestParams, out);
      }
      catch (NoSuchMethodException e)
      {
        logger.error(Messages.getErrorString("DashboardDesignerContentGenerator.ERROR_001_INVALID_METHOD_EXCEPTION") + " : " + method);
      }
      catch (InvocationTargetException e)
      {
        //get to the cause and rethrow properly
        Throwable target = e.getTargetException();
        if (!e.equals(target))
        {//just in case
          //get to the real cause
          while (target != null && target instanceof InvocationTargetException)
          {
            target = ((InvocationTargetException) target).getTargetException();
          }
        }
        if (target instanceof Exception)
        {
          throw (Exception) target;
        }
        else
        {
          throw new DashboardDesignerException(target);
        }
      }
    }
    catch (Exception e)
    {
      final String message = e.getCause() != null ? e.getCause().getClass().getName() + " - " + e.getCause().getMessage() : e.getClass().getName() + " - " + e.getMessage();
      logger.error(message, e);
    }

  }

  public void syncronize(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    // 0 - Check security. Caveat: if no path is supplied, then we're in the new parameter
    final String path = ((String) pathParams.getParameter(PathParams.FILE)).replaceAll("cdfde", "wcdf");
    if (pathParams.hasParameter(PathParams.PATH) && !hasAccess(out, path, ISolutionRepository.ACTION_UPDATE))
    {
      final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      logger.warn("Access denied for the syncronize method: " + path + " by user " + userSession.getName());
      return;
    }

    final SyncronizeCdfStructure syncCdfStructure = new SyncronizeCdfStructure();
    syncCdfStructure.syncronize(userSession, out, pathParams);
  }

  public void newdashboard(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    this.edit(pathParams, out);
  }

  public void getcomponentdefinitions(IParameterProvider pathParams, OutputStream out) throws Exception
  {
    // The ComponentManager is responsible for producing the component definitions
    ComponentManager engine = ComponentManager.getInstance();
    if (engine.getCdaDefinitions() == null)
    {
      // We want to acquire a handle for the CDA plugin.
      JSON json = getCdaDefs();
      engine.parseCdaDefinitions(json);
    }
    // Get and output the definitions
    out.write(engine.getDefinitions().getBytes());
  }

  public void getcomponentimplementations(IParameterProvider pathParams, OutputStream out) throws Exception
  {
    ComponentManager engine = ComponentManager.getInstance();
    out.write(engine.getImplementations().getBytes());
  }

  /**
   * Re-initializes the designer back-end.
   *
   * @author pdpi
   */
  public void refresh(IParameterProvider pathParams, OutputStream out) throws Exception
  {
    DependenciesManager.refresh();
    ComponentManager.getInstance().refresh();
    ComponentManager.getInstance().parseCdaDefinitions(getCdaDefs());
  }

  public void getcontent(IParameterProvider pathParams, OutputStream out) throws Exception
  {
    Dashboard dashboard = DashboardFactory.getInstance().loadDashboard(pathParams, this);
    out.write(dashboard.getContent().getBytes());
  }

  public void getheaders(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    Dashboard dashboard = DashboardFactory.getInstance().loadDashboard(pathParams, this);
    out.write(dashboard.getHeader().getBytes());
  }

  public void render(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    // Check security
    if (!hasAccess(out, getWcdfRelativePath(pathParams), ISolutionRepository.ACTION_EXECUTE))
    {
      out.write("Access Denied".getBytes(ENCODING));
      return;
    }

    // Build pieces: render dashboard, footers and headers
    Dashboard dashboard = DashboardFactory.getInstance().loadDashboard(pathParams, this);

    // Response
    setResponseHeaders(MIME_TYPE, 0, null);
    out.write(dashboard.render().getBytes(ENCODING));
  }

  private boolean hasAccess(final OutputStream out, final String path, final int actionUpdate)
          throws IOException
  {
    IPentahoSession userSession = PentahoSessionHolder.getSession();
    final ISolutionRepository solutionRepository = PentahoSystem.get(ISolutionRepository.class, userSession);
    if (solutionRepository.getSolutionFile(path, actionUpdate) == null)
    {
      return false;
    }
    return true;
  }

//  private String getDashboardStyle(final IParameterProvider pathParams) throws IOException
//  {
//    // If we have the style available as a parameter, use it. Else, load wcdf
//    if (pathParams.hasParameter("style"))
//    {
//      return pathParams.getStringParameter("style", CdfStyles.DEFAULTSTYLE);
//
//    }
//
//    String wcdfRelativePath = getWcdfRelativePath(pathParams);
//
//    final ISolutionRepository solutionRepository = PentahoSystem.get(ISolutionRepository.class, userSession);
//    final Document wcdfDoc = solutionRepository.getResourceAsDocument(wcdfRelativePath);
//
//    return XmlDom4JHelper.getNodeText(
//            "/cdf/style", wcdfDoc, CdfStyles.DEFAULTSTYLE);
//  }
  public void getcssresource(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    setResponseHeaders(CSS_TYPE, RESOURCE_CACHE_DURATION, null);
    getresource(pathParams, out);
  }

  public void getjsresource(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    setResponseHeaders(JAVASCRIPT_TYPE, RESOURCE_CACHE_DURATION, null);
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    // Set cache for 1 year, give or take.
    response.setHeader("Cache-Control", "max-age=" + 60 * 60 * 24 * 365);
    getresource(pathParams, out);
  }

  public void getuntypedresource(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    response.setHeader("Content-Type", "text/plain");
    response.setHeader("content-disposition", "inline");
    getresource(pathParams, out);

  }

  public void getimg(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    String pathString = this.parameterProviders.get("path").getStringParameter("path", "");
    String resource;
    if (pathString.split("/").length > 2)
    {
      resource = pathString.replaceAll("^/.*?/", "");
    }
    else
    {
      resource = pathParams.getStringParameter("path", "");
    }
    resource = resource.startsWith("/") ? resource : "/" + resource;

    String[] path = resource.split("/");
    String[] fileName = path[path.length - 1].split("\\.");

    final String mimeType;
    switch (FileTypes.valueOf(fileName[fileName.length - 1].toUpperCase()))
    {
      case PNG:
        mimeType = "image/png";
        break;
      case JPG:
      case JPEG:
        mimeType = "image/jpeg";
        break;
      case GIF:
        mimeType = "image/gif";
        break;
      case BMP:
        mimeType = "image/bmp";
        break;
      default:
        mimeType = "";
    }
    setResponseHeaders(mimeType, RESOURCE_CACHE_DURATION, null);
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    // Set cache for 1 year, give or take.
    response.setHeader("Cache-Control", "max-age=" + 60 * 60 * 24 * 365);
    getSolutionResource(out, resource);

  }

  public void res(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    String pathString = this.parameterProviders.get("path").getStringParameter(PathParams.PATH, "");
    String resource;
    if (pathString.split("/").length > 2)
    {
      resource = pathString.replaceAll("^/.*?/", "");
    }
    else
    {
      resource = pathParams.getStringParameter(PathParams.PATH, "");
    }
    resource = resource.startsWith("/") ? resource : "/" + resource;

    String[] path = resource.split("/");
    String[] fileName = path[path.length - 1].split("\\.");


    String mimeType;
    try
    {
      final FileTypes fileType = FileTypes.valueOf(fileName[fileName.length - 1].toUpperCase());
      mimeType = mimeTypes.get(fileType);
    }
    catch (java.lang.IllegalArgumentException ex)
    {
      mimeType = "";
    }
    catch (EnumConstantNotPresentException ex)
    {
      mimeType = "";
    }

    setResponseHeaders(mimeType, RESOURCE_CACHE_DURATION, null);
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    // Set cache for 1 year, give or take.
    response.setHeader("Cache-Control", "max-age=" + 60 * 60 * 24 * 365);
    getSolutionResource(out, resource);

  }

  public void getresource(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    String pathString = this.parameterProviders.get("path").getStringParameter("path", null);
    String resource;
    if (pathString.split("/").length > 2)
    {
      resource = pathString.replaceAll("^/.*?/", "");
    }
    else
    {
      resource = pathParams.getStringParameter("resource", null);
    }

    if (!Utils.pathStartsWith(resource, SOLUTION_DIR)
            && !Utils.pathStartsWith(resource, PLUGIN_PATH))
    {
      resource = Utils.joinPath(PLUGIN_PATH, resource);//default path
    }

    getSolutionResource(out, resource);
  }

  public void edit(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    // 0 - Check security. Caveat: if no path is supplied, then we're in the new parameter

    if (pathParams.hasParameter("path") && !hasAccess(out, getWcdfRelativePath(pathParams), ISolutionRepository.ACTION_UPDATE))
    {
      out.write("Access Denied".getBytes(ENCODING));

      return;
    }

    final String header = DependenciesManager.getInstance().getEngine("CDFDD").getDependencies();
    final HashMap<String, String> tokens = new HashMap<String, String>();
    tokens.put(DESIGNER_HEADER_TAG, header);

    // Decide whether we're in debug mode (full-size scripts) or normal mode (minified scripts)
    if (pathParams.hasParameter("debug") && pathParams.getParameter("debug").toString().equals("true"))
    {
      final String scripts = ResourceManager.getInstance().getResourceAsString(DESIGNER_SCRIPTS_RESOURCE);
      final String styles = ResourceManager.getInstance().getResourceAsString(DESIGNER_STYLES_RESOURCE);
      //DEBUG MODE
      tokens.put(DESIGNER_STYLES_TAG, styles);
      tokens.put(DESIGNER_SCRIPTS_TAG, scripts);
    }
    else
    {
      String stylesHash = packager.minifyPackage("styles");
      String scriptsHash = packager.minifyPackage("scripts");
      // NORMAL MODE
      tokens.put(DESIGNER_STYLES_TAG, "<link href=\"css/styles.css?version=" + stylesHash + "\" rel=\"stylesheet\" type=\"text/css\" />");
      tokens.put(DESIGNER_SCRIPTS_TAG, "<script type=\"text/javascript\" src=\"js/scripts.js?version=" + scriptsHash + "\"></script>");
    }
    tokens.put(FILE_NAME_TAG, getStructureRelativePath(pathParams));
    tokens.put(SERVER_URL_TAG, SERVER_URL_VALUE);
    tokens.put(DATA_URL_TAG, DATA_URL_VALUE);

    final String resource = ResourceManager.getInstance().getResourceAsString(DESIGNER_RESOURCE, tokens);

    // Cache the output - Disabled for security check reasons
    // setCacheControl();

    out.write(resource.getBytes());
  }

  public void synctemplates(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    final CdfTemplates cdfTemplates = new CdfTemplates(userSession);

    cdfTemplates.syncronize(out, pathParams);
  }

  public void listrenderers(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    out.write("{\"result\": [\"mobile\",\"blueprint\"]}".getBytes("utf-8"));
  }

  public void syncstyles(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    CdfStyles.getInstance().syncronize(userSession, out, pathParams);
  }

  public void olaputils(final IParameterProvider pathParams, final OutputStream out)
  {

    final OlapUtils olapUtils = new OlapUtils(userSession);

    olapUtils.executeOperation(pathParams);

    try
    {
      final Object result = olapUtils.executeOperation(pathParams);
      JsonUtils.buildJsonResult(out, true, result);
    }
    catch (Exception ex)
    {
      logger.fatal(ex);
      JsonUtils.buildJsonResult(out, false,
              "Exception found: " + ex.getClass().getName() + " - " + ex.getMessage());
    }

  }

  public void explorefolder(final IParameterProvider pathParams, final OutputStream out)
  {
    final String folder = pathParams.getStringParameter("dir", null);
    final String fileExtensions = pathParams.getStringParameter("fileExtensions", null);
    FileExplorer.getInstance().browse(folder, fileExtensions, userSession, out);
  }

  public Log getLogger()
  {
    return logger;
  }

  String getWcdfRelativePath(final IParameterProvider pathParams)
  {
    final String path = "/" + pathParams.getStringParameter(PathParams.SOLUTION, null)
            + "/" + pathParams.getStringParameter(PathParams.PATH, null)
            + "/" + pathParams.getStringParameter(PathParams.FILE, null);

    return path.replaceAll("//+", "/");
  }

  String getStructureRelativePath(final IParameterProvider pathParams)
  {
    String path = "/" + pathParams.getStringParameter(PathParams.SOLUTION, null)
            + "/" + pathParams.getStringParameter(PathParams.PATH, null)
            + "/" + pathParams.getStringParameter(PathParams.FILE, null);

    path = path.replaceAll("//+", "/");
    return path.replace(".wcdf", ".cdfde");
  }

  private void getSolutionResource(final OutputStream out, final String resource) throws IOException
  {

    setCacheControl();
    final String path = Utils.getSolutionPath(resource); //$NON-NLS-1$ //$NON-NLS-2$

    final File file = new File(path);

    if (!isFileWithinPath(file, PentahoSystem.getApplicationContext().getSolutionPath("")))
    {
      // File not inside solution! run away!
      throw new FileNotFoundException("Not allowed");
    }

    InputStream in = null;
    try
    {
      in = new FileInputStream(file);
      IOUtils.copy(in, out);
    }
    finally
    {
      IOUtils.closeQuietly(in);
    }
  }

  private boolean isFileWithinPath(File file, String absPathBase)
  {
    final String filePath = normalizePathSeparators(file.getAbsolutePath());
    final String basePath = normalizePathSeparators(absPathBase);
    return filePath.startsWith(basePath);
  }

  private String normalizePathSeparators(String path)
  {
    return path.replaceAll("\\\\", "/").replaceAll("/+", "/");
  }

  private void setCacheControl()
  {
    final IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
    final String maxAge = resLoader.getPluginSetting(this.getClass(), "pentaho-cdf-dd/max-age");
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    if (maxAge != null && response != null)
    {
      response.setHeader("Cache-Control", "max-age=" + maxAge);
    }
  }

  public void listdataaccesstypes(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    IPluginManager pluginManager = PentahoSystem.get(IPluginManager.class, userSession);
    IContentGenerator cda = pluginManager.getContentGenerator("cda", userSession);

    cda.setParameterProviders(parameterProviders);

    cda.setOutputHandler(outputHandler);
    ArrayList<Object> output = new ArrayList<Object>();
    HashMap<String, Object> channel = new HashMap<String, Object>();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    channel.put("output",
            outputStream);
    output.add(channel);
    channel.put(
            "method", "listDataAccessTypes");
    cda.setCallbacks(output);
    cda.createContent();
    out.write(outputStream.toString().getBytes(ENCODING));
    JSON json = JSONSerializer.toJSON(outputStream.toString());

  }

  public JSON getCdaDefs() throws Exception
  {
    IPluginManager pluginManager = PentahoSystem.get(IPluginManager.class, userSession);
    IContentGenerator cda = pluginManager.getContentGenerator("cda", userSession);
    // If CDA is present, we're going to produce components from the output of its discovery service
    if (cda != null)
    {
      // Basic setup
      cda.setParameterProviders(parameterProviders);
      cda.setOutputHandler(outputHandler);
      // We need to arrange for a callback object that will serve as a communications channel
      ArrayList<Object> output = new ArrayList<Object>();
      HashMap<String, Object> channel = new HashMap<String, Object>();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      // The outputstream provides CDA with a sink we can later retrieve data from
      channel.put("output", outputStream);
      // Setup the desired function to call on CDA's side of things.
      channel.put("method", "listDataAccessTypes");
      // Call CDA
      output.add(channel);
      cda.setCallbacks(output);
      cda.createContent();
      // pass the output to the ComponentManager
      return JSONSerializer.toJSON(outputStream.toString());
    }
    else
    {
      return null;
    }
  }

// External Editor v
  public void getfile(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    String path = pathParams.getStringParameter(PathParams.PATH, "");

    String contents = ExternalFileEditorBackend.getFileContents(path, userSession);

    setResponseHeaders("text/plain", NO_CACHE_DURATION, null);
    IOUtils.write(contents, out);
  }

  public void writefile(IParameterProvider pathParams, OutputStream out) throws Exception
  {
    String path = pathParams.getStringParameter(PathParams.PATH, null);
    String solution = pathParams.getStringParameter(PathParams.SOLUTION, null);
    String contents = pathParams.getStringParameter(PathParams.DATA, null);

    if (ExternalFileEditorBackend.writeFile(path, solution, userSession, contents))
    {//saved ok
      IOUtils.write("file '" + path + "' saved ok", out);
    }
    else
    {//error
      IOUtils.write("error saving file " + path, out);//TODO:...
    }
  }

  public void canedit(IParameterProvider pathParams, OutputStream out) throws IOException
  {
    String path = pathParams.getStringParameter(PathParams.PATH, null);

    Boolean result = ExternalFileEditorBackend.canEdit(path, userSession);
    IOUtils.write(result.toString(), out);
  }

  public void exteditor(final IParameterProvider pathParams, final OutputStream out) throws Exception
  {
    String editorPath = Utils.joinPath(PLUGIN_PATH, EXTERNAL_EDITOR_PAGE);
    IOUtils.write(ExternalFileEditorBackend.getFileContents(editorPath, userSession), out);
  }

//  External Editor ^ 
  private void init() throws IOException
  {
    this.packager = Packager.getInstance();
    Properties props = new Properties();
    String rootdir = PentahoSystem.getApplicationContext().getSolutionPath("system/" + PLUGIN_NAME);
    props.load(new FileInputStream(rootdir + "/includes.properties"));

    if (!packager.isPackageRegistered("scripts"))
    {
      String[] files = props.get("scripts").toString().split(",");
      packager.registerPackage("scripts", Packager.Filetype.JS, rootdir, rootdir + "/js/scripts.js", files);
    }
    if (!packager.isPackageRegistered("styles"))
    {
      String[] files = props.get("styles").toString().split(",");
      packager.registerPackage("styles", Packager.Filetype.CSS, rootdir, rootdir + "/css/styles.css", files);
    }
  }

  String getCdfContext()
  {

    Map<String, Object> params = new HashMap<String, Object>();
    return PluginUtils.callPlugin("pentaho-cdf", "Context", params);

  }

  String getCdfIncludes(String dashboard) throws Exception
  {
    return getCdfIncludes(dashboard, null, false, "");
  }

  String getCdfIncludes(String dashboard, String type, boolean debug, String absRoot) throws Exception
  {

    Map<String, Object> params = new HashMap<String, Object>();
    params.put("dashboardContent", dashboard);
    params.put("debug", debug);
    if (type != null)
    {
      params.put("dashboardType", type);
    }
    if (!"".equals(absRoot) && absRoot != null)
    {
      params.put("root", absRoot);
    }

    return PluginUtils.callPlugin("pentaho-cdf", "GetHeaders", params);
  }

  private void setResponseHeaders(final String mimeType, final int cacheDuration, final String attachmentName)
  {
    // Make sure we have the correct mime type
    final HttpServletResponse response = (HttpServletResponse) parameterProviders.get("path").getParameter("httpresponse");
    response.setHeader("Content-Type", mimeType);

    if (attachmentName != null)
    {
      response.setHeader("content-disposition", "attachment; filename=" + attachmentName);
    } // Cache?

    if (cacheDuration > 0)
    {
      response.setHeader("Cache-Control", "max-age=" + cacheDuration);
    }
    else
    {
      response.setHeader("Cache-Control", "max-age=0, no-store");
    }
  }

  IPentahoSession getUserSession()
  {
    return userSession;
  }
}
