package com.trixiether.dashchan.chan.warosu;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

import chan.content.ChanLocator;
import chan.content.FoolFuukaChanLocator;
import chan.content.FoolFuukaChanPerformer;
import chan.content.FoolFuukaPostsParser;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.text.ParseException;

public class WarosuChanPerformer  extends FoolFuukaChanPerformer {

    @Override
    public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
        WarosuChanLocator locator = ChanLocator.get(this);
        Uri uri = locator.createBoardUri(data.boardName, data.pageNumber);
        HttpResponse response = new HttpRequest(uri, data).setValidator(data.validator).perform();
        try (InputStream input = response.open()) {
            return new ReadThreadsResult(new FoolFuukaPostsParser(this).convertThreads(input));
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    @Override
    public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
        FoolFuukaChanLocator locator = ChanLocator.get(this);
        HttpResponse response = new HttpRequest(locator.buildPath(), data).perform();
        try (InputStream input = response.open()) {
            return new ReadBoardsResult(new WarosuBoardsParser().convert(input));
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }
}
