package amidst.mojangapi.minecraftinterface.local;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.Map;

import amidst.clazz.Classes;
import amidst.clazz.symbolic.SymbolicClass;
import amidst.clazz.translator.ClassTranslator;
import amidst.logging.Log;
import amidst.mojangapi.file.directory.VersionDirectory;
import amidst.mojangapi.minecraftinterface.IMinecraftInterface;
import amidst.mojangapi.minecraftinterface.RecognisedVersion;

public class LocalMinecraftInterfaceBuilder {
	private static final String CLIENT_CLASS_RESOURCE = "net/minecraft/client/Minecraft.class";
	private static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
	private static final String SERVER_CLASS_RESOURCE = "net/minecraft/server/MinecraftServer.class";
	private static final String SERVER_CLASS = "net.minecraft.server.MinecraftServer";

	private Map<String, SymbolicClass> symbolicClassMap;
	private RecognisedVersion recognisedVersion;

	public LocalMinecraftInterfaceBuilder(VersionDirectory versionDirectory,
			ClassTranslator translator) {
		try {
			URLClassLoader classLoader = versionDirectory.createClassLoader();
			recognisedVersion = getRecognisedVersion(classLoader);
			symbolicClassMap = Classes.createSymbolicClassMap(
					versionDirectory.getJar(), classLoader, translator);
			Log.i("Minecraft load complete.");
		} catch (RuntimeException e) {
			Log.crash(
					e.getCause(),
					"error while building local minecraft interface: "
							+ e.getMessage());
			e.printStackTrace();
		} catch (MalformedURLException e) {
			Log.crash(
					e,
					"error while building local minecraft interface: minecraft jar file has malformed url");
			e.printStackTrace();
		}
	}

	private RecognisedVersion getRecognisedVersion(URLClassLoader classLoader) {
		Log.i("Generating version ID...");
		String magicString = generateMagicString(getMainClassFields(loadMainClass(classLoader)));
		RecognisedVersion result = RecognisedVersion.from(magicString);
		Log.i("Identified Minecraft [" + result.getName()
				+ "] with magic string of " + magicString);
		return result;
	}

	private Field[] getMainClassFields(Class<?> mainClass) {
		try {
			return mainClass.getDeclaredFields();
		} catch (NoClassDefFoundError e) {
			throw new RuntimeException(
					"Unable to find critical external class while loading.\nPlease ensure you have the correct Minecraft libraries installed.",
					e);
		}
	}

	private String generateMagicString(Field[] fields) {
		String result = "";
		for (Field field : fields) {
			String typeString = field.getType().toString();
			if (typeString.startsWith("class ") && !typeString.contains(".")) {
				result += typeString.substring(6);
			}
		}
		return result;
	}

	private Class<?> loadMainClass(URLClassLoader classLoader) {
		try {
			if (classLoader.findResource(CLIENT_CLASS_RESOURCE) != null) {
				return classLoader.loadClass(CLIENT_CLASS);
			} else if (classLoader.findResource(SERVER_CLASS_RESOURCE) != null) {
				return classLoader.loadClass(SERVER_CLASS);
			} else {
				throw new RuntimeException("cannot find minecraft jar file");
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(
					"Attempted to load non-minecraft jar, or unable to locate starting point.",
					e);
		}
	}

	public IMinecraftInterface create() {
		return new LocalMinecraftInterface(symbolicClassMap, recognisedVersion);
	}
}
