package os.aios.agent;
public final class GoogleRegistrationIdTest {
    public static void main(String[] args){
        expect("123456789", "123456789");
        expect(" 123 ", "123");
        expect("-1", "18446744073709551615");
        expect("18446744073709551615", "18446744073709551615");
        expect("18446744073709551616", null);
        expect("-9223372036854775809", null);
        expect("0", null);expect(null,null);expect("",null);
        expect("a1234b",null);expect("1.5",null);
        System.out.println("PASS: 11 GSF ID normalization cases");
    }
    private static void expect(String input,String expected){
        String actual=GoogleRegistrationId.normalize(input);
        if(!java.util.Objects.equals(actual,expected))throw new AssertionError(input+": "+actual);
    }
}
