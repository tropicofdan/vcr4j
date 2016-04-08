package org.mbari.vcr4j.udp;

import org.mbari.vcr4j.VideoCommand;
import org.mbari.vcr4j.commands.VideoCommands;
import org.mbari.vcr4j.time.Timecode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Brian Schlining
 * @since 2016-02-04T13:46:00
 */
public class UDPResponseParser {

    private final Subject<UDPError, UDPError> errorObservable = new SerializedSubject<>(PublishSubject.create());
    private final Subject<Timecode, Timecode> timecodeObservable =
            new SerializedSubject<>(PublishSubject.create());
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final Pattern pattern = Pattern.compile("[0-2][0-9]:[0-5][0-9]:[0-5][0-9]:[0-2][0-9]");


    public void update(byte[] response, Optional<VideoCommand> videoCommand) {
        videoCommand.ifPresent(vc -> {
            if (vc.equals(VideoCommands.REQUEST_TIMECODE) || vc.equals(VideoCommands.REQUEST_TIMESTAMP)) {
                responseToTimecode(response, videoCommand)
                        .ifPresent(timecodeObservable::onNext);
            }
        });
    }

    public Subject<UDPError, UDPError> getErrorObservable() {
        return errorObservable;
    }

    public Subject<Timecode, Timecode> getTimecodeObservable() {
        return timecodeObservable;
    }

    private Optional<Timecode> responseToTimecode(byte[] response, Optional<VideoCommand> videoCommand) {
         /*
         * Parse the timecode response.
         */
        String result = null;

        try {
            result = new String(response, "ASCII");
        }
        catch (UnsupportedEncodingException e) {
            try {
                // Try a different encoding
                result = new String(response, "8859_1");
            }
            catch (UnsupportedEncodingException ex) {
                // result = null
                if (log.isErrorEnabled()) {
                    log.error("Timecode over UDP is using an unknown encoding");
                    errorObservable.onNext(new UDPError(false, true, videoCommand));
                }
            }
        }
        catch (Exception e) {
            if (log.isErrorEnabled()) {
                errorObservable.onNext(new UDPError(false, true, videoCommand));
            }
        }

        Optional<Timecode> timecode = Optional.empty();
        if (result != null) {
            try {
                Matcher matcher = pattern.matcher(result);

                if (matcher.find()) {
                    timecode = Optional.of(new Timecode(matcher.group(0)));
                }
            }
            catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("Problem with parsing timecode '" + result + "'", e);
                    errorObservable.onNext(new UDPError(false, true, videoCommand));
                }
            }
        }
        return timecode;
    }
}
