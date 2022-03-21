package trollogyadherent.offlineauth;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import com.mojang.util.UUIDTypeAdapter;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.common.config.Configuration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SideOnly(Side.CLIENT)
public
class Secure {

    /**
     * Username/email
     */
    static String username = "";
    /**
     * password if saved to config else empty
     */
    static String password = "";

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }

    /**
     * Mojang authentificationservice
     */
    private static final YggdrasilAuthenticationService yas;
    private static final YggdrasilUserAuthentication yua;
    private static final YggdrasilMinecraftSessionService ymss;

    /**
     * currently used to load the class
     */
    static void init() {
        String base = "reauth.";
        List<String> classes = ImmutableList.of(base + "ConfigGUI", base + "GuiFactory", base + "GuiHandler", base + "GuiLogin", base + "GuiPasswordField", base + "Main", base + "Secure", base + "VersionChecker");
        try {
            Set<ClassInfo> set = ClassPath.from(Secure.class.getClassLoader()).getTopLevelClassesRecursive("reauth");
            for (ClassInfo info : set)
                if (!classes.contains(info.getName())) {
                    throw new RuntimeException("Detected unauthorized class trying to access reauth-data! Offender: " + info.url().getPath());
                }
        } catch (IOException e) {
            throw new RuntimeException("Classnames could not be fetched!");
        }

        //VersionChecker.update();
    }

    static {
        yas = new YggdrasilAuthenticationService(Minecraft.getMinecraft().getProxy(), UUID.randomUUID().toString());
        yua = (YggdrasilUserAuthentication) yas.createUserAuthentication(Agent.MINECRAFT);
        ymss = (YggdrasilMinecraftSessionService) yas.createMinecraftSessionService();

    }

    /**
     * LOgs you in; replaces the Session in your client; and saves to config
     */
    public static void login(String user, String pw, boolean savePassToConfig) throws AuthenticationException, IllegalArgumentException, IllegalAccessException {

        /* set credentials */
        Secure.yua.setUsername(user);
        Secure.yua.setPassword(pw);

        /* login */
        Secure.yua.logIn();

        OfflineAuth.info("Login successful!");

        /* put together the new Session with the auth-data */
        String username = Secure.yua.getSelectedProfile().getName();
        String uuid = UUIDTypeAdapter.fromUUID(Secure.yua.getSelectedProfile().getId());
        String access = Secure.yua.getAuthenticatedToken();
        String type = Secure.yua.getUserType().getName();
        Sessionutil.set(new Session(username, uuid, access, type));

        /* logout to discard the credentials in the object */
        Secure.yua.logOut();

        /* save username to config */
        Secure.username = user;
        Config.config.get(Configuration.CATEGORY_GENERAL, "username", "", "Your Username").set(Secure.username);
        /* save password to config if desired */
        if (savePassToConfig) {
            Secure.password = pw;
            Config.config.get(Configuration.CATEGORY_GENERAL, "password", "", "Your Password in plaintext if chosen to save to disk").set(Secure.password);
        }
        Config.config.save();
    }

    public static void offlineMode(String username) throws IllegalArgumentException, IllegalAccessException {
        /* Create offline uuid */
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(Charsets.UTF_8));
        Sessionutil.set(new Session(username, uuid.toString(), null, "legacy"));
        OfflineAuth.info("Offline Username set!");
        Secure.username = username;
    }

    /**
     * checks online if the session is valid
     */
    static boolean SessionValid() {
        try {
            GameProfile gp = Sessionutil.get().func_148256_e();
            String token = Sessionutil.get().getToken();
            String id = UUID.randomUUID().toString();

            Secure.ymss.joinServer(gp, token, id);
            if (Secure.ymss.hasJoinedServer(gp, id).isComplete()) {
                OfflineAuth.info("Session validation successfull");
                return true;
            }
        } catch (Exception e) {
            OfflineAuth.info("Session validation failed: " + e.getMessage());
            return false;
        }
        OfflineAuth.info("Session validation failed!");
        return false;
    }

    static class Sessionutil {
        /**
         * as the Session field in Minecraft.class is static final we have to
         * access it via reflection
         */
        private static Field sessionField = ReflectionHelper.findField(Minecraft.class, "session", "S", "field_71449_j");

        static Session get() throws IllegalArgumentException, IllegalAccessException {
            return Minecraft.getMinecraft().getSession();
        }

        static void set(Session s) throws IllegalArgumentException, IllegalAccessException {
            Sessionutil.sessionField.set(Minecraft.getMinecraft(), s);
        }
    }

}