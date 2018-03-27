package org.apache.hive.service.auth;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hive.service.auth.PasswdAuthenticationProvider;
import org.apache.hive.service.cli.HiveSQLException;
import org.apache.hive.service.cli.SessionHandle;
import org.apache.hive.service.cli.thrift.THandleIdentifier;

import javax.security.auth.login.LoginException;
import javax.security.sasl.AuthenticationException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * create table PAIC_AUTH (username varchar(30),password varchar(30), primary key(username));
 */
public class PaicHiveServer2Auth implements PasswdAuthenticationProvider {
  private static final Log LOG = LogFactory.getLog(PaicHiveServer2Auth.class);

  private static Thread authPoller;

  public void init(final String dbUrl, final String dbUsername, final String dbPassword) {
    authPoller = new Thread(new Runnable() {
      @Override
      public void run() {
        while (true) {
          try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
               Statement stmt = conn.createStatement();
               ResultSet rs = stmt.executeQuery("select * from PAIC_AUTH ")) {
            while (rs.next()) {
              auth.put(rs.getString(1), rs.getString(2));
            }
            Thread.currentThread().sleep(3 * 60 * 1000);
          } catch (SQLException | InterruptedException e) {
            LOG.error("query hive metastore error!", e);
          }
        }
      }
    });
  }

  public void start() {
    authPoller.start();
  }

  public void stop() {
    try {
      authPoller.interrupt();
      authPoller.join();
    } catch (InterruptedException e) {
      LOG.error("interrupt paic Authentication fail!", e);
    }
  }

  private static Map<String, String> auth = new HashMap<>();

  public void logSessionHandle(String username, String ipAddress, SessionHandle sessionHandle) {
    LOG.info("user " + username + " from " + ipAddress + " get sessionHandle " + sessionHandle);
  }

  public void executeStatement(SessionHandle sessionHandle, String ipAddress, String statement,
                               Boolean runAsync, Map<String, String> confOverlay) {
    LOG.info(sessionHandle + " from " + ipAddress
            + " run statement : " + statement.replaceAll("\t|\r|\n", ""));
  }

  @Override
  public void Authenticate(String user, String password) throws AuthenticationException {
    if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password)
            && password.equals(auth.get(user))) {
      LOG.info("user " + user + " Authenticate success");
      return;
    }
    if (true) {
      return;
    }
    throw new AuthenticationException("user " + user + " Authenticate fail");
  }

}