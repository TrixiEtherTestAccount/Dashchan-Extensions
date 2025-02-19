package com.mishiranu.dashchan.chan.sojakparty;

import static com.mishiranu.dashchan.chan.sojakparty.SojakpartyChanConfiguration.CAPTCHA_TYPE_KAPTCHA;

import static chan.content.ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class SojakpartyChanPerformer extends ChanPerformer {
	private static final String RECAPTCHA_API_KEY = "6LdA3F4bAAAAAJ68Fh_IiaJQtxJx0Chgr3SjaAMD";
	private static final String CAPTCHA_DATA_KEY_TYPE = "captchaType";

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		SojakpartyChanConfiguration configuration = SojakpartyChanConfiguration.get(this);
		Uri uri = locator.buildPath(data.boardName, (data.isCatalog() ? "catalog"
				: Integer.toString(data.pageNumber)) + ".json");
		HttpResponse response = new HttpRequest(uri, data).setValidator(data.validator).perform();
		HttpValidator validator = response.getValidator();
		ArrayList<Posts> threads = new ArrayList<>();
		try (InputStream input = response.open();
			 JsonSerial.Reader reader = JsonSerial.reader(input)) {
			if (data.isCatalog()) {
				reader.startArray();
				while (!reader.endStruct()) {
					reader.startObject();
					while (!reader.endStruct()) {
						switch (reader.nextName()) {
							case "threads": {
								reader.startArray();
								while (!reader.endStruct()) {
									threads.add(SojakpartyModelMapper.createThread(reader,
											locator, data.boardName, true));
								}
								break;
							}
							default: {
								reader.skip();
								break;
							}
						}
					}
				}
			} else {
				reader.startObject();
				while (!reader.endStruct()) {
					switch (reader.nextName()) {
						case "threads": {
							reader.startArray();
							while (!reader.endStruct()) {
								threads.add(SojakpartyModelMapper.createThread(reader,
										locator, data.boardName, false));
							}
							break;
						}
						default: {
							reader.skip();
							break;
						}
					}
				}
			}
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		} catch (IOException e) {
			throw response.fail(e);
		}
		return new ReadThreadsResult(threads).setValidator(validator);
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "thread", data.threadNumber + ".json");
		ArrayList<Post> posts = new ArrayList<>();
		HttpResponse response = new HttpRequest(uri, data).setValidator(data.validator).perform();
		try (InputStream input = response.open();
			 JsonSerial.Reader reader = JsonSerial.reader(input)) {
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case "posts": {
						SojakpartyModelMapper.Extra extra = new SojakpartyModelMapper.Extra();
						reader.startArray();
						while (!reader.endStruct()) {
							posts.add(SojakpartyModelMapper.createPost(reader,
									locator, data.boardName, extra));
							if (extra != null) {
								extra = null;
							}
						}
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
			return new ReadPostsResult(new Posts(posts)).setFullThread(true);
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		} catch (IOException e) {
			throw response.fail(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		Uri uri = locator.buildPath();
		String responseText = new HttpRequest(uri, data).perform().readString();
		try {
			return new ReadBoardsResult(new SojakpartyBoardsParser(responseText).convert());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		throw new InvalidResponseException();
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber);
		entity.add("name", data.name);
		entity.add("email", data.optionSage ? "sage" : data.email);
		entity.add("subject", data.subject);
		entity.add("body", StringUtils.emptyIfNull(data.comment));
		entity.add("password", data.password);
		boolean spoiler = false;
		if (data.attachments != null) {
			for (int i = 0; i < data.attachments.length; i++) {
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file" + (i > 0 ? i + 1 : ""));
				if (attachment.optionSpoiler) {
					spoiler = true;
				}
			}
		}
		if (spoiler) {
			entity.add("spoiler", "on");
		}
		entity.add("json_response", "1");

		if (data.captchaData != null) {
			String captchaType = data.captchaData.get(CAPTCHA_DATA_KEY_TYPE);
			if (captchaType.equals(CAPTCHA_TYPE_RECAPTCHA_2)) {
				entity.add("g-recaptcha-response", data.captchaData.get(CaptchaData.INPUT));
			} else if (captchaType.equals(CAPTCHA_TYPE_KAPTCHA)) {
				entity.add("_KAPTCHA_KEY", data.captchaData.get(CaptchaData.CHALLENGE));
				entity.add("_KAPTCHA", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
			}
		}

		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		Uri contentUri = data.threadNumber != null ? locator.createThreadUri(data.boardName, data.threadNumber)
				: locator.createBoardUri(data.boardName, 0);
		String responseText = new HttpRequest(contentUri, data).perform().readString();
		try {
			AntispamFieldsParser.parseAndApply(responseText, entity, "board", "thread", "name", "email",
					"subject", "body", "password", "file", "spoiler", "json_response", "_KAPTCHA", "_KAPTCHA_NOJS", "_KAPTCHA_KEY");
		} catch (ParseException e) {
			throw new InvalidResponseException();
		}
		Uri uri = locator.buildPath("post.php");
		JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.addHeader("Referer", locator.buildPath().toString())
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}

		String redirect = jsonObject.optString("redirect");
		if (!StringUtils.isEmpty(redirect)) {
			uri = locator.buildPath(redirect);
			String threadNumber = locator.getThreadNumber(uri);
			String postNumber = locator.getPostNumber(uri);
			return new SendPostResult(threadNumber, postNumber);
		}
		String errorMessage = jsonObject.optString("error");
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("The body was") || errorMessage.contains("must be at least")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			} else if (errorMessage.contains("You must upload an image")) {
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			} else if (errorMessage.contains("mistyped the verification")) {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			} else if (errorMessage.contains("was too long")) {
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			} else if (errorMessage.contains("The file was too big") || errorMessage.contains("is longer than")) {
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			} else if (errorMessage.contains("Thread locked")) {
				errorType = ApiException.SEND_ERROR_CLOSED;
			} else if (errorMessage.contains("Invalid board")) {
				errorType = ApiException.SEND_ERROR_NO_BOARD;
			} else if (errorMessage.contains("Thread specified does not exist")) {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			} else if (errorMessage.contains("Unsupported image format")) {
				errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
			} else if (errorMessage.contains("Maximum file size")) {
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			} else if (errorMessage.contains("Your IP address")) {
				errorType = ApiException.SEND_ERROR_BANNED;
			} else if (errorMessage.contains("That file")) {
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			} else if (errorMessage.contains("Flood detected")) {
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			CommonUtils.writeLog("Soyjak party send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("delete", "1", "board", data.boardName,
				"password", data.password, "json_response", "1");
		for (String postNumber : data.postNumbers) {
			entity.add("delete_" + postNumber, "1");
		}
		if (data.optionFilesOnly) {
			entity.add("file", "on");
		}
		Uri uri = locator.buildPath("post.php");
		JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		if (jsonObject.optBoolean("success")) {
			return null;
		}
		String errorMessage = jsonObject.optString("error");
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("Wrong password")) {
				errorType = ApiException.DELETE_ERROR_PASSWORD;
			} else if (errorMessage.contains("before deleting that")) {
				errorType = ApiException.DELETE_ERROR_TOO_NEW;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			CommonUtils.writeLog("Soyjak.party delete message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("report", "1", "board", data.boardName,
				"reason", StringUtils.emptyIfNull(data.comment), "json_response", "1");
		for (String postNumber : data.postNumbers) {
			entity.add("delete_" + postNumber, "1");
		}
		Uri uri = locator.buildPath("post.php");
		JSONObject jsonObject = null;
		try {
			jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		if (jsonObject.optBoolean("success")) {
			return null;
		}
		String errorMessage = jsonObject.optString("error");
		if (errorMessage != null) {
			CommonUtils.writeLog("Soyjak.party report message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws InvalidResponseException, HttpException {
		String captchaType = data.captchaType;
		SojakpartyChanLocator locator = SojakpartyChanLocator.get(this);
		SojakpartyChanConfiguration configuration = SojakpartyChanConfiguration.get(this);
		ReadCaptchaResult result;
		if (SojakpartyChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType)) {
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.REFERER, locator.buildPath().toString());
			captchaData.put(CaptchaData.API_KEY, RECAPTCHA_API_KEY);
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, CAPTCHA_TYPE_RECAPTCHA_2);
			result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
					.setValidity(SojakpartyChanConfiguration.Captcha.Validity.IN_BOARD_SEPARATELY);
		} else if (SojakpartyChanConfiguration.CAPTCHA_TYPE_NONE.equals(captchaType)) {
			captchaType = null;
			result = new ReadCaptchaResult(CaptchaState.SKIP, null);
		} else if (CAPTCHA_TYPE_KAPTCHA.equals(captchaType)) {
			if (data.threadNumber != null && !configuration.isKaptchaRepliesEnabled()) {
				return new ReadCaptchaResult(CaptchaState.SKIP, null);
			}
			String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789=-";
			SecureRandom rnd = new SecureRandom();
			StringBuilder keyBuilder = new StringBuilder(64);
			for(int i = 0; i < 64; i++) {
				keyBuilder.append(chars.charAt(rnd.nextInt(chars.length())));
			}
			String key = keyBuilder.toString();

			Uri uri = Uri.parse("https://sys.kolyma.net/kaptcha/kaptcha.php?key=" + key);
			Bitmap image;
			String response = new HttpRequest(uri, data).perform().readString();
			byte[] imageBytes = Base64.decode(response.split(", ")[1], 0);
			image = imageBytes.length == 0 ? null
					: BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
			if (image == null) {
				throw new InvalidResponseException(new Exception("Image is null"));
			}
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, CAPTCHA_TYPE_KAPTCHA);
			captchaData.put(CaptchaData.CHALLENGE, key);
			result = new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
		} else {
			throw new IllegalStateException();
		}
		if (!CommonUtils.equals(data.captchaType, captchaType)) {
			result.setCaptchaType(captchaType);
		}
		return result;
	}
}