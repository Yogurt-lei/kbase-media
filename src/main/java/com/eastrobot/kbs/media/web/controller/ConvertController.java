package com.eastrobot.kbs.media.web.controller;

import com.alibaba.fastjson.JSONObject;
import com.eastrobot.kbs.media.exception.BusinessException;
import com.eastrobot.kbs.media.model.AiType;
import com.eastrobot.kbs.media.model.ResponseMessage;
import com.eastrobot.kbs.media.model.ResultCode;
import com.eastrobot.kbs.media.model.VacType;
import com.eastrobot.kbs.media.model.aitype.ASR;
import com.eastrobot.kbs.media.model.aitype.OCR;
import com.eastrobot.kbs.media.model.aitype.TTS;
import com.eastrobot.kbs.media.model.aitype.VAC;
import com.eastrobot.kbs.media.service.ConvertService;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.eastrobot.kbs.media.model.Constants.*;

/**
 * ConvertController
 *
 * @author <a href="yogurt_lei@foxmail.com">Yogurt_lei</a>
 * @version v1.0 , 2018-03-29 12:01
 */
@Api(tags = "转换接口")
@Slf4j
@RestController()
@RequestMapping("/convert")
@SuppressWarnings("unchecked")
public class ConvertController {

    @Autowired
    private ConvertService converterService;

    @ApiOperation("(AI识别通用接口)视频,音频,图片,转换为文本.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file", value = "待转换文件", dataType = "__file", required = true, paramType = "form")
    })
    @PostMapping(
            value = "",
            produces = {MediaType.APPLICATION_JSON_UTF8_VALUE},
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ResponseMessage recognition(MultipartFile file, HttpServletRequest request) {
        return getRecognitionResponse(file, AiType.GENERIC_RECOGNITION, request);
    }

    @ApiOperation("自动语音识别[ASR].")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file", value = "待转换文件", dataType = "__file", required = true, paramType = "form")
    })
    @PostMapping(
            value = "/asr",
            produces = {MediaType.APPLICATION_JSON_UTF8_VALUE},
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ResponseMessage<ASR> asr(MultipartFile file, HttpServletRequest request) {
        return getRecognitionResponse(file, AiType.ASR, request);
    }

    @ApiOperation("光学图像识别[OCR].")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file", value = "待转换文件", dataType = "__file", required = true, paramType = "form")
    })
    @PostMapping(
            value = "/ocr",
            produces = {MediaType.APPLICATION_JSON_UTF8_VALUE},
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ResponseMessage<OCR> ocr(MultipartFile file, HttpServletRequest request) {
        return getRecognitionResponse(file, AiType.OCR, request);
    }

    @ApiOperation("视频解析转写[VAC].")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "file", value = "待转换文件", dataType = "__file", required = true, paramType = "form"),
            @ApiImplicitParam(name = "type", value = "0-默认值，根据视频中的帧画和音轨进行解析, 1-仅解析视频中的帧画，2-仅解析视频中的音轨", dataType = "Integer")
    })
    @PostMapping(
            value = "/vac",
            produces = {MediaType.APPLICATION_JSON_UTF8_VALUE},
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ResponseMessage<VAC> vac(MultipartFile file, Integer type, HttpServletRequest request) {
        VacType vacType = VacType.VAC;
        type = type==null?0:type;
        switch (type){
            case 1:
                vacType = VacType.VAC_OCR;
                break;
            case 2:
                vacType = VacType.VAC_ASR;
                break;
            default:

        }
        request.setAttribute(VAC_TYPE, vacType.name());
        return getRecognitionResponse(file, AiType.VAC, request);
    }

    @ApiOperation(value = "文本语音合成[TTS]", notes = "根据输入的文本，返回json，包含一段base64的音频码，可采用 base64 转 blob 用于前端播放")

    @PostMapping(
            value = "/tts",
            produces = {MediaType.APPLICATION_JSON_UTF8_VALUE},
            consumes = {MediaType.APPLICATION_JSON_UTF8_VALUE}
    )
    public ResponseMessage<TTS> tts(@ApiParam(name = "reqBody", value = "参数格式：{\"text\": \"我和我的祖国\"}", required = true) @RequestBody String reqBody) {
        try {
            JSONObject reqJson = Optional.ofNullable(reqBody)
                    .filter(StringUtils::isNotBlank)
                    .map(JSONObject::parseObject)
                    .orElseThrow(BusinessException::new);

            String text = reqJson.getString("text");

            Map<String, Object> ttsParam = ImmutableMap.<String, Object>builder()
                    .put(IS_ASYNC_PARSE, false)
                    .put(AI_TYPE, AiType.TTS)
                    .put(AI_TTS_TEXT, text)
                    .put(AI_TTS_OPTION, Collections.emptyMap())
                    .put(AI_RESOURCE_FILE_PATH, DigestUtils.md5Hex(text))
                    .build();
            return converterService.driver(ttsParam);
        } catch (BusinessException e) {
            return new ResponseMessage<>(ResultCode.PARAM_ERROR);
        }
    }

    private ResponseMessage getRecognitionResponse(MultipartFile file, AiType aiType, HttpServletRequest request) {
        try {
            Optional.ofNullable(file).filter(v -> !v.isEmpty()).orElseThrow(BusinessException::new);
            String md5 = DigestUtils.md5Hex(file.getBytes());
            String targetFile;
            try {
                targetFile = converterService.uploadFile(file, md5, false);
            } catch (Exception e) {
                return new ResponseMessage(ResultCode.FILE_UPLOAD_FAILURE);
            }

            Map<String, Object> recognitionParam = ImmutableMap.<String, Object>builder()
                    .put(IS_ASYNC_PARSE, false)
                    .put(AI_WHETHER_EACH_IMAGE_EXTRACT_KEYWORD, Optional.ofNullable(request.getParameter(AI_WHETHER_EACH_IMAGE_EXTRACT_KEYWORD)).orElse(""))
                    .put(AI_WHETHER_NEED_VIDEO_POSTER, Optional.ofNullable(request.getParameter(AI_WHETHER_NEED_VIDEO_POSTER)).orElse(""))
                    .put(AI_RESOURCE_FILE_PATH, targetFile)
                    .put(AI_TYPE, aiType)
                    .put(VAC_TYPE, Optional.ofNullable(request.getAttribute(VAC_TYPE)).orElse(VacType.VAC.name()))
                    .build();

            return converterService.driver(recognitionParam);
        } catch (Exception e) {
            return new ResponseMessage<>(ResultCode.PARAM_ERROR);
        }
    }
}
