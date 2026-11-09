package os.aios.agent;
import org.json.*;
public final class NotificationClassifierTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("classifier check "+checks);}
 static JSONObject n(String title,String text)throws Exception{return new JSONObject().put("title",title).put("text",text).put("package","example");}
 public static void main(String[] args)throws Exception{
  for(String[] pair:new String[][]{{"司机已到达","请尽快前往上车点"},{"司机即将到达提醒","司机将在2分钟内到达，请提前前往上车点"},{"司機已到達","請前往上車點"},{"Your driver has arrived","Meet your driver at the pickup point"}})check(NotificationClassifier.urgent(n(pair[0],pair[1])).getInt("importance")==2);
  for(String[] pair:new String[][]{{"司机已到达优惠券","领取打车优惠"},{"行程结束","确认账单"},{"到达新优惠","立即领取"},{"司机已到达","观看热门视频"}})check(NotificationClassifier.urgent(n(pair[0],pair[1]))==null);
  for(String label:new String[]{"urgent","normal","advertising","suspected_fraud"}){
   JSONObject result=NotificationClassifier.decode(new JSONObject().put("label",label).toString());check(result.getString("decision").equals(label.equals("urgent")||label.equals("normal")?"allow":"filter"));
  }
  for(String raw:new String[]{"{}","{\"label\":\"提醒\"}","{\"label\":\"normal\",\"other\":1}","not json"}){boolean fail=false;try{NotificationClassifier.decode(raw);}catch(JSONException e){fail=true;}check(fail);}
  JSONObject safe=NotificationClassifier.input(n("x","a".repeat(9000)).put("identity","private-id").put("key","private-key"));check(!safe.has("identity")&&!safe.has("key"));check(safe.getString("text").length()==1600);
  JSONArray rules=new JSONArray().put(new JSONObject().put("id","explicit").put("action","filter").put("importance",0));
  check(NotificationRules.evaluate(rules,n("司机已到达","请前往上车点"),(a,b,c)->{throw new AssertionError("rule must be terminal");}).getString("decision").equals("filter"));
  for(String[] item:new String[][]{
   {"支付成功","您已支付37.40元，收款方：高德打车","allow"},
   {"到账通知","您收到一笔转账，金额128.00元。","allow"},
   {"微信支付","微信支付收款128.00元","allow"},
   {"到账提醒","您的储蓄卡尾号1234入账5800元，交易类型为工资。","allow"},
   {"信用卡账单","本期应还款额876.25元，请于11月15日前还款。","allow"},
   {"包裹已到驿站","请凭取件码26-30-58领取您的快递。","allow"},
   {"登录验证码","您的登录验证码为123456，5分钟内有效，请勿泄露给他人。","allow"},
   {"安全提醒","您的账号刚刚在新设备登录，如非本人操作请检查账号安全。","allow"},
   {"红包到账提醒","88元优惠红包待领取，购物满300可用，点击领取","filter"},
   {"额度到账","您有50000元借款额度待领取，年利率低至3.6%，立即申请。","filter"},
   {"邀请打卡","邀请您为地点打卡，参加活动赢取奖励","filter"},
   {"最后30分钟","今晚大促即将结束，立即抢购，满300减100！","filter"},
   {"Payment received","Your account received $128.00","allow"},
   {"Verification code","Your login verification code is 654321. Do not share it.","allow"},
   {"Parcel delivery","Collect your parcel with pickup code 12-34","allow"},
   {"Coupon discount","Claim now to save 30%","filter"},
   {"Account verification","Reply with your verification code to restore access","filter"}
  }){JSONObject d=NotificationClassifier.rules(n(item[0],item[1]));check(d!=null&&d.getString("decision").equals(item[2]));}
  for(String[] item:new String[][]{{"好友消息","今天的优惠券帮我留一下"},{"还款问题","我朋友说贷款额度不可信"},{"验证码安全","不要回复验证码给陌生人"},{"Driver news","A story about driver arrivals"}})check(NotificationClassifier.rules(n(item[0],item[1]))==null);
  System.out.println("PASS "+checks+" classification contract and urgent-service assertions");
 }
}
