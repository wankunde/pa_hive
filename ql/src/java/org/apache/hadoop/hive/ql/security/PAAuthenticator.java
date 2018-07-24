package org.apache.hadoop.hive.ql.security;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.session.SessionState;

import java.util.Arrays;

/**
 * Created by WANKUN603 on 2018-01-30.
 */
public class PAAuthenticator extends HadoopDefaultAuthenticator {

  private static final Log LOG = LogFactory.getLog(PAAuthenticator.class);

  private final static String DSP_KEY = "dsp.submit.user";

  @Override
  public String getUserName() {

    String dspKey = SessionState.get().getHiveVariables().get(DSP_KEY);
    if (StringUtils.isEmpty(dspKey))
      dspKey = SessionState.get().getConf().get(DSP_KEY);
    LOG.info("dspKey : " + dspKey);
    if (StringUtils.isNotEmpty(dspKey)) {
      String[] fields = dspKey.split(":");
      LOG.info("fields : " + Arrays.toString(fields));
      String[] users = fields[0].split("#");
      LOG.info("users : " + Arrays.toString(users));
      if (users.length > 0)
        return users[users.length - 1].replace("@pingan.com.cn", "");
    }
    return super.getUserName();
  }
}
