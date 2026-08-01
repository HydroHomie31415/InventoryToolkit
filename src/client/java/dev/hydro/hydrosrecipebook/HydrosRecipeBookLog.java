package dev.hydro.hydrosrecipebook;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class HydrosRecipeBookLog {
	private static final Logger LOGGER = LogUtils.getLogger();

	private HydrosRecipeBookLog() {
	}

	static void warn(String message, Throwable throwable) {
		LOGGER.warn("[Hydro's Recipe Book] {}", message, throwable);
	}

	static void info(String message, Object... arguments) {
		LOGGER.info("[Hydro's Recipe Book] " + message, arguments);
	}
}
