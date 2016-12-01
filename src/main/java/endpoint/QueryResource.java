package endpoint;

import com.google.gson.Gson;
import jdbc.Query;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Created by jamescross91 on 05/11/2015.
 */
public class QueryResource extends ServerResource {

    static Logger logger = Logger.getLogger(QueryResource.class);

    @Get
    public String represent() {
        return "Post your SQL queries to this endpoint";
    }

    @Post
    public Representation processQuery(Representation entity) throws IOException, JSONException {
        JSONObject json = new JsonRepresentation(entity).getJsonObject();

        if(!json.has("sql")) {
            return errorRepresentation("SQL parameter not supplied");
        }

        if(!json.has("auth")) {
            return errorRepresentation("Auth token not supplied");
        } else {
            final String token = String.valueOf(json.get("auth"));
            Properties authTokens = loadAuthTokens();
            boolean authenticated = false;
            for (Enumeration en = authTokens.propertyNames(); en.hasMoreElements();) {
                String division  = (String) en.nextElement();
                String division_token = authTokens.getProperty(division);
                if(division_token.equals(token)) {
                    authenticated = true;
                    logger.info("Authenticated division '" + division + "'");
                }
            }
            if(! authenticated) {
                return errorRepresentation("Incorrect auth token supplied");
            }
        }

        List<JSONObject> list = Query.execute(json.getString("sql"), resultSet -> {
            ArrayList<JSONObject> objList = new ArrayList<JSONObject>();
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                JSONObject jsonObject = jsonForRow(resultSet, metaData);
                objList.add(jsonObject);
            }

            return objList;
        });

        JSONArray array = new JSONArray(list);
        return new JsonRepresentation(array);
    }

    private Properties loadAuthTokens() {
        final String filename = "/auth_tokens.properties";
        logger.debug("Reading auth tokens from classpath file: " + filename);
        Properties authTokens = new Properties();
        try(InputStream is = this.getClass().getResourceAsStream(filename)) {
            authTokens.load(is);
            return authTokens;
        } catch (IOException e) {
            final String msg = "Error attempting to read/load auth_tokens.properties: " + e.getMessage();
            logger.error(msg, e);
            e.printStackTrace();
            throw new RuntimeException(msg);
        }
    }

    private JSONObject jsonForRow(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        JSONObject rowObject = new JSONObject();

        int columnCount = metaData.getColumnCount();
        for(int i = 1; i <= columnCount; i++) {
            try {
                rowObject.put(metaData.getColumnName(i), resultSet.getObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return rowObject;
    }

    private JsonRepresentation errorRepresentation(String errMessage) {
        JSONObject object = new JSONObject();
        try {
            object.put("message", errMessage);
            object.put("success", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new JsonRepresentation(object);
    }
}
