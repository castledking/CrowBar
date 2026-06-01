package codes.castled.crowbar;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Method;

public final class VersionCompat {
    private static Method profileNameMethod;
    private static Method profileIdMethod;

    public static String getGameProfileName(GameProfile profile) {
        if (profileNameMethod == null) {
            try {
                profileNameMethod = GameProfile.class.getMethod("name");
            } catch (NoSuchMethodException e) {
                try {
                    profileNameMethod = GameProfile.class.getMethod("getName");
                } catch (NoSuchMethodException e2) {
                    return getProfileId(profile).toString();
                }
            }
        }
        try {
            return (String) profileNameMethod.invoke(profile);
        } catch (Exception e) {
            return getProfileId(profile).toString();
        }
    }

    private static Object getProfileId(GameProfile profile) {
        if (profileIdMethod == null) {
            try {
                profileIdMethod = GameProfile.class.getMethod("id");
            } catch (NoSuchMethodException e) {
                try {
                    profileIdMethod = GameProfile.class.getMethod("getId");
                } catch (NoSuchMethodException e2) {
                    return "unknown";
                }
            }
        }
        try {
            return profileIdMethod.invoke(profile);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
