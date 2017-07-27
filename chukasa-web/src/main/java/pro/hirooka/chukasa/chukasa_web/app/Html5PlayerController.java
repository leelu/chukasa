package pro.hirooka.chukasa.chukasa_web.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import pro.hirooka.chukasa.chukasa_api.app.api.v1.helper.ChukasaUtility;
import pro.hirooka.chukasa.chukasa_api.app.api.v1.helper.IChukasaBrowserDetector;
import pro.hirooka.chukasa.chukasa_common.domain.configuration.ChukasaConfiguration;
import pro.hirooka.chukasa.chukasa_common.domain.configuration.SystemConfiguration;
import pro.hirooka.chukasa.chukasa_common.domain.enums.HardwareAccelerationType;
import pro.hirooka.chukasa.chukasa_common.domain.enums.StreamingType;
import pro.hirooka.chukasa.chukasa_common.domain.service.ICommonUtilityService;
import pro.hirooka.chukasa.chukasa_common.domain.service.ISystemService;
import pro.hirooka.chukasa.chukasa_hls.domain.configuration.HlsConfiguration;
import pro.hirooka.chukasa.chukasa_hls.domain.model.ChukasaModel;
import pro.hirooka.chukasa.chukasa_hls.domain.model.ChukasaSettings;
import pro.hirooka.chukasa.chukasa_hls.domain.service.IChukasaModelManagementComponent;
import pro.hirooka.chukasa.chukasa_hls.domain.service.ICoordinatorService;
import pro.hirooka.chukasa.chukasa_web.domain.model.Html5Player;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

import static pro.hirooka.chukasa.chukasa_common.domain.constants.ChukasaConstants.ALTERNATIVE_HLS_PLAYER;

@Slf4j
@Controller
@RequestMapping("video")
public class Html5PlayerController {

    @Autowired
    ChukasaConfiguration chukasaConfiguration;
    @Autowired
    SystemConfiguration systemConfiguration;
    @Autowired
    HlsConfiguration hlsConfiguration;
    @Autowired
    IChukasaModelManagementComponent chukasaModelManagementComponent;
    @Autowired
    HttpServletRequest httpServletRequest;
    @Autowired
    ISystemService systemService;
    @Autowired
    IChukasaBrowserDetector chukasaBrowserDetector;
    @Autowired
    ICommonUtilityService commonUtilityService;
    @Autowired
    ICoordinatorService taskCoordinatorService;

    @RequestMapping(method = RequestMethod.POST)
    String play(Model model, @Validated ChukasaSettings chukasaSettings, BindingResult bindingResult){

        if(bindingResult.hasErrors()){
            return "index";
        }

        HardwareAccelerationType hardwareAccelerationType = systemService.getHardwareAccelerationType();
        if(hardwareAccelerationType == HardwareAccelerationType.UNKNOWN){
            return "index";
        }

        ChukasaUtility.initializeRunner(chukasaModelManagementComponent, systemConfiguration);
        if(chukasaModelManagementComponent.get().size() > 0){
            return "index";
        }

        log.info("ChukasaSettings -> {}", chukasaSettings.toString());

        ChukasaModel chukasaModel = new ChukasaModel();
        chukasaModel.setChukasaConfiguration(chukasaConfiguration);
        chukasaModel.setSystemConfiguration(systemConfiguration);
        chukasaModel.setHlsConfiguration(hlsConfiguration);
        chukasaModel.setChukasaSettings(chukasaSettings);

        chukasaModel.setUuid(UUID.randomUUID());
        chukasaModel.setAdaptiveBitrateStreaming(0);
        chukasaModel.setHardwareAccelerationType(hardwareAccelerationType);

        chukasaModel = ChukasaUtility.operateEncodingSettings(chukasaModel);
        if(chukasaModel == null){
            return "index";
        }

        String servletRealPath = httpServletRequest.getSession().getServletContext().getRealPath("");
        String streamRootPath = commonUtilityService.getStreamRootPath(servletRealPath);
        chukasaModel.setStreamRootPath(streamRootPath);
        chukasaModel = ChukasaUtility.createChukasaDerectory(chukasaModel);
        chukasaModel = ChukasaUtility.calculateTimerTaskParameter(chukasaModel);

        String playlistURI = ChukasaUtility.buildM3u8URI(chukasaModel);
        if(playlistURI.equals("/")){
            return "index";
        }

        chukasaModelManagementComponent.create(0, chukasaModel);

        //chukasaTaskService.execute(0);
        taskCoordinatorService.execute();

        Html5Player html5PlayerModel = new Html5Player();
        html5PlayerModel.setPlaylistURI(playlistURI);
        model.addAttribute("html5PlayerModel", html5PlayerModel);

        String userAgent = httpServletRequest.getHeader("user-agent");
        log.info("userAgent: {}", userAgent);
        if(chukasaBrowserDetector.isNativeSupported(userAgent)){
            return "player";
        }else if(chukasaBrowserDetector.isAlternativeSupported(userAgent)){
            return ALTERNATIVE_HLS_PLAYER + "-player";
        }else{
            return "index";
        }
    }

    // TODO: GET 修正
    @RequestMapping(method = RequestMethod.GET)
    String play(Model model,
                @RequestParam StreamingType streamingtype,
                @RequestParam int ch,
                @RequestParam int videobitrate,
                @RequestParam boolean encrypted){

        ChukasaSettings chukasaSettings = new ChukasaSettings();
        chukasaSettings.setAdaptiveBitrateStreaming(0);
        chukasaSettings.setStreamingType(streamingtype);
        chukasaSettings.setPhysicalLogicalChannel(ch);
        chukasaSettings.setVideoBitrate(videobitrate);
        //chukasaSettings.setVideoResolutionType(VideoResolutionType.HD);
        //chukasaSettings.setCaptureResolutionType(VideoResolutionType.HD);
        chukasaSettings.setAudioBitrate(128);
        chukasaSettings.setCanEncrypt(encrypted);

        log.info("ChukasaSettings -> {}", chukasaSettings.toString());

        return "index";
    }

    @RequestMapping(value = "/stop", method = RequestMethod.GET)
    String stop(){
        taskCoordinatorService.cancel();
        taskCoordinatorService.stop();
        //chukasaStopper.stop();
        return "redirect:/video/remove";
    }

    @RequestMapping(value = "/remove", method = RequestMethod.GET)
    String remove(){
        taskCoordinatorService.remove();
        //taskCoordinatorService.cancel();
//        String streamRootPath = commonUtilityService.getStreamRootPath(httpServletRequest.getSession().getServletContext().getRealPath(""));
//        if(Files.exists(new File(streamRootPath).toPath())) {
//            chukasaRemover.setStreamRootPath(streamRootPath);
//            chukasaRemover.remove();
//        }else {
//            log.warn("cannot remove files bacause streamRootPath: {} does not exist.", streamRootPath);
//        }
        return "redirect:/menu";
    }
}
