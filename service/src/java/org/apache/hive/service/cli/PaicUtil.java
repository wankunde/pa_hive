package org.apache.hive.service.cli;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created by WANKUN603 on 2018-04-12.
 */
public class PaicUtil {

  private static Set<String> PROVINCE_CODES = new HashSet<String>();
  private static SimpleDateFormat dateFormat = null;

  static {
    PROVINCE_CODES.add("11");
    PROVINCE_CODES.add("12");
    PROVINCE_CODES.add("13");
    PROVINCE_CODES.add("14");
    PROVINCE_CODES.add("15");
    PROVINCE_CODES.add("21");
    PROVINCE_CODES.add("22");
    PROVINCE_CODES.add("23");
    PROVINCE_CODES.add("31");
    PROVINCE_CODES.add("32");
    PROVINCE_CODES.add("33");
    PROVINCE_CODES.add("34");
    PROVINCE_CODES.add("35");
    PROVINCE_CODES.add("36");
    PROVINCE_CODES.add("37");
    PROVINCE_CODES.add("41");
    PROVINCE_CODES.add("42");
    PROVINCE_CODES.add("43");
    PROVINCE_CODES.add("44");
    PROVINCE_CODES.add("45");
    PROVINCE_CODES.add("46");
    PROVINCE_CODES.add("50");
    PROVINCE_CODES.add("51");
    PROVINCE_CODES.add("52");
    PROVINCE_CODES.add("53");
    PROVINCE_CODES.add("54");
    PROVINCE_CODES.add("61");
    PROVINCE_CODES.add("62");
    PROVINCE_CODES.add("63");
    PROVINCE_CODES.add("64");
    PROVINCE_CODES.add("65");
    PROVINCE_CODES.add("66");
    PROVINCE_CODES.add("71");
    PROVINCE_CODES.add("81");
    PROVINCE_CODES.add("82");

    dateFormat = new SimpleDateFormat("yyyyMMdd");
    dateFormat.setLenient(false);
  }

  private static final Pattern isPhone = Pattern.compile("^(1[2-9])\\d{9}$");

  public static String hidePrivateMsg(String value) {
    if (value == null)
      return value;
    else if (isPhone.matcher(value).matches())  // phone
      return value.substring(0, 3) + "****" + value.substring(7, 11);
    else if (isValidIdCard(value))  // id card
      return value.substring(0, 4) + "**********" + value.substring(value.length() - 4, value.length());
    else
      return value;
  }

  public static void main(String[] args) {
    System.out.println(hidePrivateMsg("32032419880608517X"));
    System.out.println(hidePrivateMsg("15618758658"));
    System.out.println(hidePrivateMsg("1718_两融外部抢客"));
  }


  public static boolean isValidIdCard(String idCard) {
    if (idCard == null || idCard.trim().equals("")) {
      return false;
    }
    try {
      String identityId = idCard.trim();
      if (identityId.length() != 15 && identityId.length() != 18) {
        return false;
      }
      String province = identityId.substring(0, 2);
      if (!PROVINCE_CODES.contains(province)) {
        return false;
      }
      if (!isValidDate(identityId)) {
        return false;
      }

      if (identityId.length() == 18) {
        try {
          // check the last 4 char, they are must be numbers
          Integer.valueOf(identityId.substring(14, 17));
        } catch (NumberFormatException e) {
          return false;
        }

        if (verify(identityId.toUpperCase())) {
          return true;
        } else {
          return false;
        }
      } else if (identityId.length() == 15) {
        try {
          // check the last 4 char, they are must be numbers
          Integer.valueOf(identityId.substring(12));
        } catch (NumberFormatException e) {
          return false;
        }
        return true;
      }
    } catch (Exception ex) {
      return false;
    }
    return false;
  }

  private static boolean isValidDate(String idCard) {
    try {
      String identityId = idCard.trim();
      if (identityId.length() == 18) {
        String birthday = identityId.substring(6, 14);
        dateFormat.parse(birthday);
      } else if (identityId.length() == 15) {
        String birthday = identityId.substring(6, 12);
        dateFormat.parse("19" + birthday);
      } else {
        return false;
      }
    } catch (Exception ex) {
      return false;
    }
    return true;
  }

  private static boolean verify(String idCardNumber) throws Exception {
    if (idCardNumber == null || idCardNumber.length() != 18) {
      return false;
    }
    return getVerifyCode(idCardNumber) == idCardNumber.charAt(idCardNumber.length() - 1);
  }

  public static char getVerifyCode(String idCardNumber) throws Exception {
    if (idCardNumber == null || idCardNumber.length() < 17) {
      throw new Exception("不合法的身份证号码");
    }
    char[] Ai = idCardNumber.toCharArray();
    int[] Wi = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    char[] verifyCode = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    int S = 0;
    int Y;
    for (int i = 0; i < Wi.length; i++) {
      S += (Ai[i] - '0') * Wi[i];
    }
    Y = S % 11;
    return verifyCode[Y];
  }
}
