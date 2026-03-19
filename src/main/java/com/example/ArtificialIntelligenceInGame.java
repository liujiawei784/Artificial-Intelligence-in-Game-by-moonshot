package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * 兜底方案：硬编码API Key，无需配置文件
 * 适配1.21.11 + Fabric API 0.141.3
 */
public class ArtificialIntelligenceInGame implements ModInitializer {
	public static final String MOD_ID = "aiingame";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	// ========== 核心修改：直接硬编码你的Kimi API Key ==========
	public static String KIMI_API_KEY = ""; // 替换成你的真实Key
	// =======================================================

	@Override
	public void onInitialize() {
		// 注释掉配置文件加载逻辑（你已删除配置文件）
		// loadConfig();

		// 校验Key是否填写
		if (KIMI_API_KEY.equals("sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")) {
			LOGGER.error("=== 请修改代码中 KIMI_API_KEY 为你的真实Kimi API Key！ ===");
		} else {
			LOGGER.info("=== Kimi API Key 已配置，模组加载完成 ===");
		}

		registerSbCommand();
	}

	/**
	 * 注册/sb指令
	 */
	private void registerSbCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						literal("sb")
								.then(argument("content", greedyString())
										.executes(context -> {
											ServerCommandSource source = context.getSource();
											String inputContent = context.getArgument("content", String.class);
											handleKimiRequest(source, inputContent);
											return 1;
										})
								)
				)
		);
	}

	/**
	 * 核心逻辑：校验→请求→返回结果
	 */
	private void handleKimiRequest(ServerCommandSource source, String content) {
		// 1. 校验API Key是否填写（硬编码版）
		if (KIMI_API_KEY.equals("sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") || KIMI_API_KEY.isEmpty()) {
			sendMessage(source, "错误：请先在代码中填写真实的Kimi API Key！", Formatting.RED);
			return;
		}

		// 2. 校验局域网连接
		if (!isLanConnected()) {
			sendMessage(source, "错误：未连接局域网！请先连接局域网后重试。", Formatting.RED);
			return;
		}

		// 3. 异步请求Kimi API
		sendMessage(source, "正在请求Kimi AI，请稍候...", Formatting.GRAY);

		new Thread(() -> {
			String kimiAnswer = callKimiAPI(content);
			if (kimiAnswer == null) {
				sendMessage(source, "请求失败！请检查API Key是否正确或网络连接。", Formatting.RED);
			} else {
				sendMessage(source, "Kimi回答：\n" + kimiAnswer, Formatting.GREEN);
			}
		}).start();
	}

	/**
	 * 通用消息发送方法
	 */
	private void sendMessage(ServerCommandSource source, String message, Formatting color) {
		source.sendMessage(Text.literal(message).formatted(color));
		source.getServer().getPlayerManager().broadcast(
				Text.literal("[KimiAPI] " + message).formatted(color),
				false
		);
	}

	/**
	 * 检测局域网连接状态
	 */
	private boolean isLanConnected() {
		try {
			InetAddress gateway = InetAddress.getByName("192.168.1.1");
			if (gateway.isReachable(2000)) {
				return true;
			}
			InetAddress localHost = InetAddress.getLocalHost();
			return localHost.isReachable(1000);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 调用Kimi官方API
	 */
	private String callKimiAPI(String prompt) {
		try {
			URI uri = URI.create("https://api.moonshot.cn/v1/chat/completions");
			URL url = uri.toURL();
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setRequestProperty("Authorization", "Bearer " + KIMI_API_KEY);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);

			// 构造请求体
			JsonObject requestBody = new JsonObject();
			requestBody.addProperty("model", "moonshot-v1-8k");
			requestBody.addProperty("temperature", 0.7);

			JsonArray messages = new JsonArray();
			JsonObject userMsg = new JsonObject();
			userMsg.addProperty("role", "user");
			userMsg.addProperty("content", prompt);
			messages.add(userMsg);
			requestBody.add("messages", messages);

			// 发送请求
			try (OutputStream os = conn.getOutputStream()) {
				byte[] requestData = requestBody.toString().getBytes(StandardCharsets.UTF_8);
				os.write(requestData);
				os.flush();
			}

			// 解析响应
			if (conn.getResponseCode() == 200) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
					StringBuilder response = new StringBuilder();
					String line;
					while ((line = br.readLine()) != null) {
						response.append(line);
					}
					JsonObject responseJson = JsonParser.parseString(response.toString()).getAsJsonObject();
					return responseJson.getAsJsonArray("choices")
							.get(0).getAsJsonObject()
							.getAsJsonObject("message")
							.get("content").getAsString();
				}
			} else {
				LOGGER.error("Kimi API响应错误，状态码：{}", conn.getResponseCode());
			}
			conn.disconnect();
		} catch (Exception e) {
			LOGGER.error("调用Kimi API异常：", e);
		}
		return null;
	}

	// ========== 删除配置文件加载方法（你已删除配置文件） ==========
	// private void loadConfig() { ... }
}