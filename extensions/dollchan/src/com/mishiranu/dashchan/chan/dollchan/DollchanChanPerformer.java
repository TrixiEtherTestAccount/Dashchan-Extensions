package com.mishiranu.dashchan.chan.dollchan;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.WakabaChanLocator;
import chan.content.WakabaChanPerformer;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.CookieBuilder;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.SimpleEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DollchanChanPerformer extends WakabaChanPerformer {

	private static final String COOKIE_AUTHORIZATION = "AUTHORIZATION";

	private String userPassword;

	private boolean checkPassword(String password) {
		if (password == null)
		{
			return false;
		}
		if (userPassword == null)
		{
			return false;
		}
		return userPassword.equals(password);
	}

	@Override
	protected List<Posts> parseThreads(String boardName, InputStream input) throws IOException, chan.text.ParseException {
		return new DollchanPostsParser(this, boardName).convertThreads(input);
	}

	@Override
	protected List<Post> parsePosts(String boardName, InputStream input) throws IOException, chan.text.ParseException {
		return new DollchanPostsParser(this, boardName).convertPosts(input);
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) {
		return new ReadBoardsResult(new BoardCategory(null, new Board[] {
			new Board("ukr", "Україна"),
			new Board("de", "Scripts"),
			new Board("btb", "Bytebeat"),
			new Board("test", "Testing")}));
	}

	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data)
			throws HttpException {
		String boardName = data.authorizationData[0];
		String password = data.authorizationData[1];
		return new CheckAuthorizationResult(authorizeUser(data, boardName, password) != null);
	}

	private String authorizeUserFromConfiguration(HttpRequest.Preset preset)
			throws HttpException {
		String[] authorizationData = DollchanChanConfiguration.get(this).getUserAuthorizationData();
		String boardName = authorizationData[0];
		String password = authorizationData[1];
		if (!checkPassword(password)) {
			if (password != null) {
				return authorizeUser(preset, boardName, password);
			} else {
				userPassword = null;
			}
		}
		return userPassword;
	}

	private String authorizeUser(HttpRequest.Preset preset, String boardName, String password)
			throws HttpException {
		userPassword = null;
		DollchanChanLocator locator = ChanLocator.get(this);

		Uri uri = locator.buildPath(boardName, "imgboard.php?manage");
		HttpResponse response = new HttpRequest(uri, preset).setPostMethod(
			new UrlEncodedEntity("managepassword", password)).perform();

		DollchanChanConfiguration configuration = DollchanChanConfiguration.get(this);

		configuration.storeCookie(COOKIE_AUTHORIZATION,
			response.getCookieValue("PHPSESSID"), "Authorization");

		return updateAuthorizationData(response.readString(), password);
	}

	private String updateAuthorizationData(String response, String password) {
		if (response != null && response.contains("?manage&logout\">Log Out<")) {
			this.userPassword = password;
			return password;
		}
		userPassword = null;
		return null;
	}

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("<div class=\"reply\".*?>(.*?)</div>");
	private static final Pattern PATTERN_POST_ERROR_UNCOMMON = Pattern.compile("<h2>(.*?)</h2>");
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		DollchanChanLocator locator = DollchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "inc", "captcha.php");
		HttpResponse response = new HttpRequest(uri, data).perform();
		Bitmap image = response.readBitmap();
		String captchaId = response.getCookieValue("PHPSESSID");
		if (image == null || captchaId == null) {
			throw new InvalidResponseException();
		}

		int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
		Bitmap newImage = Bitmap.createBitmap(pixels, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
		image.recycle();
		image = CommonUtils.trimBitmap(newImage, 0x00000000);
		if (image == null) {
			image = newImage;
		} else if (image != newImage) {
			newImage.recycle();
		}

		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, captchaId);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
	}

	private CookieBuilder buildCookies(CaptchaData data) {
		CookieBuilder builder = new CookieBuilder();
		if (data != null)
		{
			builder.append("PHPSESSID", data.get(CaptchaData.CHALLENGE));
		}
		return builder;
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();

		entity.add("parent", data.threadNumber != null ? data.threadNumber : "0");
		entity.add("name", data.name != null ? data.name : "");
		entity.add("subject", data.subject != null ? data.subject : "");
		entity.add("message", data.comment);
		entity.add("password", data.password);
		if (data.optionSage) {
			entity.add("email", "sage");
		} else {
			entity.add("email", data.email != null ? data.email : "");
		}
		if (data.attachments != null) {
			for (int i = 0; i < data.attachments.length; i++) {
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file[]");
			}
		}

		DollchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php");

		if (data.captchaData != null) {
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
		}

		HttpResponse response = new HttpRequest(uri, data).setPostMethod(entity).
			addCookie(buildCookies(data.captchaData)).
			setRedirectHandler(HttpRequest.RedirectHandler.NONE).perform();

		String responseText;
		if (response.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
			uri = response.getRedirectedUri();
			String path = uri.getPath();
			if (path == null) {
				throw new InvalidResponseException();
			}
			if (!path.startsWith("/error")) {
				String threadNumber = locator.getThreadNumber(uri);
				if (threadNumber == null) {
					throw new InvalidResponseException();
				}
				return new SendPostResult(threadNumber, null);
			}
			responseText = new HttpRequest(uri, data).addCookie(buildCookies(data.captchaData)).perform().readString();
		} else {
			responseText = response.readString();
		}

		String errorMessage = null;
		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (matcher.find()) {
			errorMessage = matcher.group(1);
		} else {
			matcher = PATTERN_POST_ERROR_UNCOMMON.matcher(responseText);
			if (matcher.find()) {
				errorMessage = matcher.group(1);
			}
		}
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("Incorrect CAPTCHA text entered")) {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			} else {
				CommonUtils.writeLog("Dollchan send message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		return null;
	}

	protected static Pattern POSTER_IP = Pattern.compile("<input type=\"hidden\" name=\"bans\" value=\"(.*?)\">");
	protected static Pattern REASON_PARSER = Pattern.compile("([0-9]+) (.*+)");

	private CookieBuilder buildCookiesWithAuthorizationPass() {
		CookieBuilder builder = new CookieBuilder();
		String auth = DollchanChanConfiguration.get(this).getCookie(COOKIE_AUTHORIZATION);
		if (auth != null)
		{
			builder.append("PHPSESSID", auth);
		}
		return builder;
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data)
			throws HttpException, ApiException
	{
		if (authorizeUserFromConfiguration(data) == null)
		{
			throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
		}

		if (DollchanChanConfiguration.REPORDING_DELETE.equals(data.type))
		{
			for (String postNumber : data.postNumbers) {
				DollchanChanLocator locator = DollchanChanLocator.get(this);

				Uri uri = locator.buildPath(data.boardName,
					"imgboard.php?manage&delete=" + postNumber);
				String response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();
				if (response.contains("Enter an administrator or moderator password")) {
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		if (DollchanChanConfiguration.REPORDING_BAN.equals(data.type))
		{
			for (String postNumber : data.postNumbers) {
				DollchanChanLocator locator = DollchanChanLocator.get(this);
				Uri uri = locator.buildPath(data.boardName,
					"imgboard.php?manage=&moderate=" + postNumber);
				String response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();

				Matcher m = POSTER_IP.matcher(response);
				if (!m.find())
				{
					throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
				}

				String ip = m.group(1);

				uri = locator.buildPath(data.boardName,
						"imgboard.php?manage&bans");

				Matcher reason = REASON_PARSER.matcher(data.comment);
				if (!reason.find())
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				int days;

				try
				{
					days = Integer.parseInt(reason.group(1));
				}
				catch (NumberFormatException e)
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				MultipartEntity entity = new MultipartEntity(
					"ip", ip,
					"expire", Integer.toString(days * 86400),
					"reason", reason.group(2)
				);

				response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).setPostMethod(entity).
						perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		if (DollchanChanConfiguration.REPORDING_BAN_DELETE_ALL.equals(data.type))
		{
			for (String postNumber : data.postNumbers) {
				DollchanChanLocator locator = DollchanChanLocator.get(this);
				Uri uri = locator.buildPath(data.boardName,
						"imgboard.php?manage=&moderate=" + postNumber);
				String response = new HttpRequest(uri, data).
						addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();

				Matcher m = POSTER_IP.matcher(response);
				if (!m.find())
				{
					throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
				}

				String ip = m.group(1);

				Matcher reason = REASON_PARSER.matcher(data.comment);
				if (!reason.find())
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				int days;

				try
				{
					days = Integer.parseInt(reason.group(1));
				}
				catch (NumberFormatException e)
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				MultipartEntity entity = new MultipartEntity(
					"ip", ip,
					"expire", Integer.toString(days * 86400),
					"reason", reason.group(2)
				);

				response = new HttpRequest(
						locator.buildPath(data.boardName, "imgboard.php?manage&bans"),
					data).addCookie(buildCookiesWithAuthorizationPass()).setPostMethod(entity).
					perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}

				response = new HttpRequest(
						locator.buildPath(data.boardName, "imgboard.php?manage&delall=" + ip),
					data).addCookie(buildCookiesWithAuthorizationPass()).
					perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException {
		UrlEncodedEntity entity = new UrlEncodedEntity(
			"password", data.password
		);

		for (String postNumber : data.postNumbers) {
			entity.add("delete", postNumber);
		}

		DollchanChanLocator locator = DollchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php?delete");
		String response = new HttpRequest(uri, data).setPostMethod(entity)
			.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString();
		if (response.contains("Invalid password")) {
			throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
		}
		return new SendDeletePostsResult();
	}
}
