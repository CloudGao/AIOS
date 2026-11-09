package os.aios.agent;

/** Gservices stores a decimal 64-bit value; never substitute Settings.Secure.ANDROID_ID. */
final class GoogleRegistrationId {
    static String normalize(String raw){
        if(raw==null)return null;
        String value=raw.trim();
        if(!value.matches("-?[0-9]+"))return null;
        try{
            long id=value.startsWith("-")?Long.parseLong(value):Long.parseUnsignedLong(value);
            return id==0?null:Long.toUnsignedString(id);
        }catch(NumberFormatException e){return null;}
    }
}
