package com.paic.data.hive;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hive.service.auth.PasswdAuthenticationProvider;

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


    private static Map<String, String> auth = new HashMap<>();
    private static Timer timer = null;

    @Override
    public void Authenticate(String username, String password) throws AuthenticationException {

        if (timer == null) {
            synchronized (this.getClass()) {
                if (timer == null) {
                    timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            PaicHiveServer2Auth.querydb();
                        }
                    }, 0, 60 * 1000);
                }
                try {
                    Thread.currentThread().sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(username)
                && password.equals(auth.get(username))) {
            LOG.info("user [" + username + "] auth check ok .. ");
        } else {
            LOG.info("user [" + username + "] auth check fail .. ");
            throw new AuthenticationException("user [" + username + "] auth check fail .. ");
        }
    }

    public static void querydb() {
        HiveConf hiveConf = new HiveConf();
        Configuration conf = new Configuration(hiveConf);
        String username = conf.get("javax.jdo.option.ConnectionUserName");
        String password = conf.get("javax.jdo.option.ConnectionPassword");
        String url = conf.get("javax.jdo.option.ConnectionURL");

        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection(url, username, password);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select * from PAIC_AUTH ");
            while (rs.next()) {
                auth.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            LOG.error("query hive metastore error!", e);
        } finally {
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
            }

            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
            }
        }
    }


}
