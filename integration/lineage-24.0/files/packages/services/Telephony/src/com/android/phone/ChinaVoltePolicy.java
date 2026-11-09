package com.android.phone;

/** Device/SIM-scoped availability baseline; never claims registration or provisioning. */
final class ChinaVoltePolicy {
    private ChinaVoltePolicy() {}
    static boolean applies(String device, String simOperator) {
        if (!"frankel".equals(device) || simOperator == null) return false;
        switch (simOperator) {
            case "46000": case "46002": case "46004": case "46007":
            case "46008": case "46013": // China Mobile
            case "46001": case "46006": case "46009": // China Unicom
            case "46003": case "46011": case "46012": // China Telecom
                return true;
            default: return false;
        }
    }
}
