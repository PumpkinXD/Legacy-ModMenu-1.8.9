package io.github.prospector.modmenu.config;

import com.google.common.collect.Sets;
import com.google.gson.*;
import io.github.prospector.modmenu.ModMenu;
import io.github.prospector.modmenu.config.option.BooleanConfigOption;
import io.github.prospector.modmenu.config.option.ConfigOptionStorage;
import io.github.prospector.modmenu.config.option.EnumConfigOption;
import io.github.prospector.modmenu.config.option.StringSetConfigOption;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.stream.Collectors;

public class ModMenuConfigManager {
	private static final Logger LOGGER = LogManager.getLogger("Mod Menu | Config Manager");
	private static final File file = new File( FabricLoader.getInstance().getGameDir().resolve("config").toString(), ModMenu.MOD_ID + ".json" );

	public static void initializeConfig() {
		load();
	}

	private static void load() {
		try {
			if (!file.exists()) {
				save();
			}
			if (file.exists()) {
				BufferedReader br = new BufferedReader(new FileReader(file));
				JsonObject json = new JsonParser().parse(br).getAsJsonObject();

				for (Field field : ModMenuConfig.class.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
						if (StringSetConfigOption.class.isAssignableFrom(field.getType())) {
							JsonArray jsonArray = json.getAsJsonArray(field.getName().toLowerCase(Locale.ROOT));
							if (jsonArray != null) {
								StringSetConfigOption option = (StringSetConfigOption) field.get(null);
								ConfigOptionStorage.setStringSet(option.getKey(), Sets.newHashSet(jsonArray).stream().map(JsonElement::getAsString).collect(Collectors.toSet()));
							}
						} else if (BooleanConfigOption.class.isAssignableFrom(field.getType())) {
							JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive(field.getName().toLowerCase(Locale.ROOT));
							if (jsonPrimitive != null && jsonPrimitive.isBoolean()) {
								BooleanConfigOption option = (BooleanConfigOption) field.get(null);
								ConfigOptionStorage.setBoolean(option.getKey(), jsonPrimitive.getAsBoolean());
							}
						} else if (EnumConfigOption.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType) {
							JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive(field.getName().toLowerCase(Locale.ROOT));
							if (jsonPrimitive != null && jsonPrimitive.isString()) {
								Type generic = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
								if (generic instanceof Class<?>) {
									EnumConfigOption<?> option = (EnumConfigOption<?>) field.get(null);
									Enum<?> found = null;
									//noinspection unchecked
									for ( Enum<?> value : ( ( Class< Enum<?> > ) generic ).getEnumConstants() ) {
										if ( value.name().toLowerCase(Locale.ROOT).equals( jsonPrimitive.getAsString() ) ) {
											found = value;
											break;
										}
									}
									if (found != null) {
										ConfigOptionStorage.setEnumTypeless( option.getKey(), found );
									}
								}
							}
						}
					}
				}
			}
		} catch (FileNotFoundException | IllegalAccessException e) {
			LOGGER.error( "Couldn't load Mod Menu configuration file; reverting to defaults", e );
		}
	}

	public static void save() {
		ModMenu.clearModCountCache();

		JsonObject config = new JsonObject();

		try {
			for (Field field : ModMenuConfig.class.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
					if (BooleanConfigOption.class.isAssignableFrom(field.getType())) {
						BooleanConfigOption option = (BooleanConfigOption) field.get(null);
						config.addProperty(field.getName().toLowerCase(Locale.ROOT), ConfigOptionStorage.getBoolean(option.getKey()));
					} else if (StringSetConfigOption.class.isAssignableFrom(field.getType())) {
						StringSetConfigOption option = (StringSetConfigOption) field.get(null);
						JsonArray array = new JsonArray();
						ConfigOptionStorage.getStringSet(option.getKey()).forEach(array::add);
						config.add(field.getName().toLowerCase(Locale.ROOT), array);
					} else if (EnumConfigOption.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType) {
						Type generic = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
						if (generic instanceof Class<?>) {
							EnumConfigOption<?> option = (EnumConfigOption<?>) field.get(null);
							//noinspection unchecked
							config.addProperty(
								field.getName().toLowerCase(Locale.ROOT),
								ConfigOptionStorage.getEnumTypeless( option.getKey(), ( Class<Enum<?>> ) generic ).name().toLowerCase(Locale.ROOT)
							);
						}
					}
				}
			}
		} catch (IllegalAccessException e) {
			LOGGER.error( "Couldn't save Mod Menu configuration file", e );
		}

		String jsonString = ModMenu.GSON.toJson(config);

		try (FileWriter fileWriter = new FileWriter(file)) {
			fileWriter.write(jsonString);
		} catch (IOException e) {
			LOGGER.error( "Couldn't save Mod Menu configuration file", e );
		}
	}
}
