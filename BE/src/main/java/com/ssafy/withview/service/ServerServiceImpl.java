package com.ssafy.withview.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.transaction.Transactional;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.withview.dto.ChannelDto;
import com.ssafy.withview.dto.ServerDto;
import com.ssafy.withview.dto.UserDto;
import com.ssafy.withview.entity.ServerEntity;
import com.ssafy.withview.entity.UserEntity;
import com.ssafy.withview.entity.UserServerEntity;
import com.ssafy.withview.repository.ServerRepository;
import com.ssafy.withview.repository.UserRepository;
import com.ssafy.withview.repository.UserServerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerServiceImpl implements ServerService {
	private final ServerRepository serverRepository;
	private final UserServerRepository userServerRepository;
	private final UserRepository userRepository;
	private final AmazonS3 s3client;
	private final RedisTemplate redisTemplate;

	@Value(value = "${cloud.aws.s3.bucket}")
	private String bucketName;

	@Value(value = "${DEFAULT_IMG}")
	private String DEFAULT_IMG;

	@Value(value = "${FRONT_URL}")
	private String FRONT_URL;

	@Override
	public ChannelDto findChannelByName(String channelName) {
		return null;
	}

	@Transactional
	@Override
	public ServerDto insertServer(ServerDto serverDto, MultipartFile multipartFile) throws Exception {
		ServerDto result;
		try {
			if (multipartFile != null) {
				System.out.println("이미지 저장 시작");
				if (!s3client.doesBucketExist(bucketName)) {
					s3client.createBucket(bucketName);
				}
				String originalName = "";
				File backgroundImgFile;
				String backgroundImgSearchName = "";
				UUID uuid = UUID.randomUUID();
				String extend = "";
				//사진이 없는경우 로고 사진으로 대체
				if (multipartFile == null) {
					originalName = DEFAULT_IMG;
				}
				//사진이 있으면 해당 사진을 배경화면으로
				else {
					originalName = multipartFile.getOriginalFilename();
				}
				System.out.println("원본 파일 이름 : " + originalName);
				extend = originalName.substring(originalName.lastIndexOf('.'));
				// #2 - 원본 파일 이름 저장
				serverDto.setBackgroundImgOriginalName(originalName);

				// #3 - 저장용 랜덤 파일 이름 저장
				backgroundImgSearchName = uuid.toString() + extend;
				backgroundImgFile = File.createTempFile(uuid.toString(), extend);
				FileUtils.copyInputStreamToFile(multipartFile.getInputStream(), backgroundImgFile);

				// #5 - 이미지 서버 저장
				s3client.putObject(bucketName, "server-background/" + backgroundImgSearchName, backgroundImgFile);
				// #6 - DB 저장
				serverDto.setBackgroundImgSearchName(uuid.toString() + extend);
			}

			ServerEntity serverEntity = ServerDto.toEntity(serverDto);
			result = ServerEntity.toDto(serverRepository.save(serverEntity));
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}

		return result;
	}

	@Transactional
	@Override
	public ServerDto updateServer(ServerDto serverDto, MultipartFile multipartFile) throws Exception {
		ServerEntity serverEntity = serverRepository.findBySeq(serverDto.getSeq());
		System.out.println("대상 서버 " + serverEntity);
		if (serverEntity == null) {
			throw new Exception("대상 서버가 없음");
		}
		if (multipartFile != null) {
			System.out.println("=== 파일 변경 ===");
			if (!s3client.doesBucketExist(bucketName)) {
				s3client.createBucket(bucketName);
			}
			// #2 - 원본 파일 이름 저장
			String originalName = multipartFile.getOriginalFilename();
			serverDto.setBackgroundImgOriginalName(originalName);

			// #3 - 저장용 랜덤 파일 이름 저장
			String extend = originalName.substring(originalName.lastIndexOf('.'));
			UUID uuid = UUID.randomUUID();
			String backgroundImgSearchName = uuid.toString() + extend;
			// #4 - 파일 임시 저장
			backgroundImgSearchName = uuid.toString() + extend;
			File backgroundImgFile = File.createTempFile(uuid.toString(), extend);
			FileUtils.copyInputStreamToFile(multipartFile.getInputStream(), backgroundImgFile);

			// #5 - 기존 이미지 삭제
			s3client.deleteObject(bucketName, "server-background/" + serverEntity.getBackgroundImgSearchName());

			// #6 - 이미지 서버 저장
			s3client.putObject(bucketName, "server-background/" + backgroundImgSearchName, backgroundImgFile);

			// #7 - DB 저장
			serverDto.setBackgroundImgSearchName(uuid.toString() + extend);
			backgroundImgFile.delete();    //기존 임시 저장용 파일 삭제
		}
		System.out.println(serverDto);
		serverEntity.update(serverDto);

		return ServerEntity.toDto(serverEntity);
	}

	@Override
	public ServerDto findServerBySeq(Long serverSeq) {
		return ServerEntity.toDto(serverRepository.findBySeq(serverSeq));
	}

	@Override
	public List<ServerDto> findAllServerByUserSeq(Long userSeq) {
		List<ServerDto> userServerDtoList = new ArrayList<>();
		UserEntity userEntity = userRepository.findBySeq(userSeq)
			.orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보가 없습니다."));
		List<UserServerEntity> userServerEntityList = userServerRepository.findAllServerByUserEntity(userEntity);

		for (UserServerEntity userServerEntity : userServerEntityList) {
			ServerDto serverDto= ServerEntity.toDto(userServerEntity.getServerEntity());
			serverDto.setPeopleCnt(findAllUsersByServerSeq(serverDto.getSeq()).size());
			userServerDtoList.add(serverDto);
		}

		return userServerDtoList;
	}

	@Override
	public List<UserDto> findAllUsersByServerSeq(Long serverSeq) {
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);
		List<UserServerEntity> userServerEntityList = userServerRepository.findAllUserByServerEntity(serverEntity);
		List<UserDto> userDtoList = new ArrayList<>();

		for (int i = 0; i < userServerEntityList.size(); i++) {
			userDtoList.add(UserEntity.toDto(userServerEntityList.get(i).getUserEntity()));
		}

		return userDtoList;
	}

	@Transactional
	@Override
	public void deleteServer(Long serverSeq, Long userSeq) throws Exception {
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);

		if (serverEntity == null) {
			throw new Exception("해당 서버가 없습니다.");
		}

		if (serverEntity.getHostSeq() != userSeq) {
			throw new Exception("해당 서버를 삭제할 권한이 없습니다.");
		}

		serverRepository.delete(serverEntity);
		//S3에 있는 이미지 삭제
		s3client.deleteObject(bucketName, "server-background/" + serverEntity.getBackgroundImgSearchName());
	}

	@Override
	public List<ServerDto> findAllServer() {
		List<ServerEntity> serverEntityList = serverRepository.findAll();
		List<ServerDto> serverDtoList = new ArrayList<>();

		for (int i = 0; i < serverEntityList.size(); i++) {
			serverDtoList.add(ServerEntity.toDto(serverEntityList.get(i)));
		}
		return serverDtoList;
	}

	@Transactional
	@Override
	public void enterServer(Long serverSeq, Long userSeq) throws Exception {
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);

		if (serverEntity == null) {
			throw new Exception("해당 서버가 없습니다.");
		}

		UserEntity userEntity = userRepository.findBySeq(userSeq)
			.orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보가 없습니다."));

		if (userEntity == null) {
			throw new Exception("해당 유저가 없습니다.");
		}

		UserServerEntity userServerEntity = userServerRepository.findByServerEntityAndUserEntity(serverEntity,
			userEntity);

		if (userServerEntity != null) {
			throw new Exception("이미 서버에 존재합니다.");
		}

		userServerEntity = UserServerEntity.builder()
			.serverEntity(serverEntity)
			.userEntity(userEntity)
			.build();
		userServerRepository.save(userServerEntity);
	}

	@Transactional
	@Override
	public void leaveServer(Long serverSeq, Long userSeq) throws Exception {
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);
		UserEntity userEntity = userRepository.findBySeq(userSeq)
			.orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보가 없습니다."));

		if (serverEntity == null || userEntity == null) {
			throw new Exception("서버 혹은 유저가 없습니다.");
		}

		UserServerEntity userServerEntity = userServerRepository.findByServerEntityAndUserEntity(serverEntity,
			userEntity);

		if (userServerEntity == null) {
			throw new Exception("이미 서버에 존재하지 않습니다.");
		}

		userServerRepository.delete(userServerEntity);
	}

	@Override
	public String insertInviteCode(Long serverSeq, Long userSeq) throws Exception {
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);
		if (serverEntity == null) {
			throw new Exception("대상 서버가 존재하지 않습니다.");
		}
		ServerDto serverDto = ServerEntity.toDto(serverEntity);


		UserEntity userEntity = userRepository.findBySeq(userSeq)
			.orElseThrow(() -> new IllegalArgumentException("일치하는 회원 정보가 없습니다."));

		if (userEntity == null) {
			throw new Exception("대상 유저가 존재하지 않습니다.");
		}

		UserServerEntity userServerEntity = userServerRepository.findByServerEntityAndUserEntity(serverEntity,
			userEntity);

		if (userServerEntity == null) {
			throw new Exception("가입하지 않아 초대링크를 생성할 수 없습니다.");
		}

		Integer leftLimit = 48; // numeral '0'
		Integer rightLimit = 122; // letter 'z'
		Integer targetStringLength = 10;

		Random random = new Random();

		String generatedString = random.ints(leftLimit, rightLimit + 1)
			.filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
			.limit(targetStringLength)
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString();

		String url = FRONT_URL +"serverenter/"+generatedString;

		redisTemplate.opsForValue().set(generatedString,serverDto.toJson());

		return url;
	}

	@Override
	public ServerDto validateInviteCode(String inviteCode) throws Exception {
		log.info("초대 링크 ",inviteCode);
		String jsonStr = (String)redisTemplate.opsForValue().get(inviteCode);
		log.info("서버 정보 json ",jsonStr);

		if(jsonStr == null){
			throw new Exception("유효하지 않은 초대코드입니다.");
		}
		
		ObjectMapper objectMapper = new ObjectMapper();

		ServerDto serverDto = objectMapper.readValue(jsonStr,ServerDto.class);

		if(serverDto == null){
			throw new Exception("서버 dto로 파싱이 실패했습니다.");
		}

		return serverDto;
	}
}
