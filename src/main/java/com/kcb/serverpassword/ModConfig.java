package com.kcb.serverpassword;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("serverpassword.json");

	public String password = "changeme";
	public boolean enabled = true;
	public int maxLoginAttempts = 5;
	public int reminderIntervalTicks = 100;

	public static ModConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				ServerPasswordMod.LOGGER.error("Failed to read config, using defaults", e);
			}
		}

		ModConfig config = new ModConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			ServerPasswordMod.LOGGER.error("Failed to save config", e);
		}
	}
}
