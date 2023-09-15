package com.yundingshuyuan.recruit.service.handler;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.alibaba.fastjson.JSON;
import com.yundingshuyuan.enums.CheckInRespStatusEnum;
import com.yundingshuyuan.recruit.dao.QrCodeCheckInMapper;
import com.yundingshuyuan.recruit.domain.CheckInEvent;
import com.yundingshuyuan.recruit.domain.po.InterviewCheckInPo;
import com.yundingshuyuan.recruit.domain.po.LectureCheckInPo;
import com.yundingshuyuan.recruit.domain.vo.CheckInEventVo;
import com.yundingshuyuan.vo.BasicResultVO;
import io.github.linpeilie.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;


/**
 * 宣讲会签到策略
 *
 * @author wys
 */
@Slf4j
@Component
public class LectureCheckInHandler implements CheckInHandler<LectureCheckInPo>, CheckInHandlerFactory<LectureCheckInPo> {

    /**
     * 密码 32 字节
     */
    static final String PASSWORD = "+-YD_Lecture=)YunDing2023.!*%010";
    @Autowired
    private Converter converter;

    @Override
    public BasicResultVO<Boolean> doCheckIn(CheckInEvent<?> event, QrCodeCheckInMapper mapper) {
        // event.data是 JSONObject
        LectureCheckInPo data = JSON.parseObject(event.getData().toString(), LectureCheckInPo.class);
        Long userId = data.getUserId();
        Long lectureId = data.getLectureId();
        // TODO: 具体逻辑还得根据情况改
        /*两种检查方案，根据-宣讲会放票检验具体实现-调整选择方案，待删其中一个*/
        //1.方案一: 根据已经看过宣讲会的记录应该被逻辑删除(假设) 如果限票校验不需要根据deleted 保留
        long result = mapper.selectTicketByUserAndLectureId(userId, lectureId);
        if (result < 1) {
            log.info("userId {} 已经签到过 lectureId {}", userId, lectureId);
            return new BasicResultVO<>(CheckInRespStatusEnum.CHECKIN_RECORD_EXIST.getCode(),
                    CheckInRespStatusEnum.CHECKIN_RECORD_EXIST.getMsg() + getBindingName());
        }
        /*// 宣讲会有效期判断
        LocalDateTime lectureGarbTime = mapper.getLectureGarbTime(lectureId);
        LocalDateTime expiredTime = lectureGarbTime.plusDays(1).plusHours(2);
        if (LocalDateTime.now().isAfter(expiredTime)) {
            log.info("lectureId {} 允许签到时间已过", userId, lectureId);
            throw new RuntimeException("允许签到时间已过");
        }*/
        // 通过 userId 修改 is_lectured
        mapper.updateIsLectureByUserId(userId);
        // 逻辑删除该票记录 搭配方案一，如果方案一删除，该段删除
        mapper.deleteTicketByUserAndLectureId(userId, lectureId);
        return BasicResultVO.success(true);
    }

    @Override
    public String getBindingName() {
        return "宣讲会";
    }

    @Override
    public LectureCheckInPo handleByOpenId(String openId, QrCodeCheckInMapper mapper) {
        return mapper.selectTicketByOpenId(openId);
    }

    @Override
    public String encipher(LectureCheckInPo data, long createTimestamp, long expireTimestamp) {
        // 获取盐值
        byte[] salt = new byte[16];
        System.arraycopy(SecureUtil.generateKey("AES", 128).getEncoded(), 0, salt, 0, 16);
        // 使用盐值和密码生成密钥
        AES aes = new AES(Mode.CTS, Padding.PKCS5Padding, PASSWORD.getBytes(), salt);
        CheckInEvent event = CheckInEvent.builder()
                .data(data)
                .createTimestamp(createTimestamp)
                .expireTimestamp(expireTimestamp).build();
        String encryptedEvent = aes.encryptHex(event.toString());
        String saltAndEncryptedData = Base64.encode(salt) + Base64.encode(encryptedEvent, StandardCharsets.UTF_8);
        return JSON.toJSONString(new CheckInEventVo(getBindingName(), saltAndEncryptedData));
    }

    @Override
    public CheckInEvent decipher(String saltAndEncryptedData) {
        // 取出
        byte[] saltFromStr = Base64.decode(saltAndEncryptedData.substring(0, 24));
        String encryptedDataFromStr = Base64.decodeStr(saltAndEncryptedData.substring(24));
        // 使用盐值和密码生成密钥
        AES aes = new AES(Mode.CTS, Padding.PKCS5Padding, PASSWORD.getBytes(), saltFromStr);
        return JSON.parseObject(aes.decryptStr(encryptedDataFromStr), CheckInEvent.class);
    }

    @Override
    public CheckInHandler<LectureCheckInPo> createCheckInHandler() {
        return this;
    }
}
