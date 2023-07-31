package com.ssafy.withview.service;

import com.amazonaws.services.s3.AmazonS3;
import com.ssafy.withview.repository.ChannelRepository;
import com.ssafy.withview.repository.ServerRepository;
import com.ssafy.withview.repository.UserRepository;
import com.ssafy.withview.repository.UserServerRepository;
import com.ssafy.withview.repository.dto.ChannelDto;
import com.ssafy.withview.repository.dto.ServerDto;
import com.ssafy.withview.repository.entity.ChannelEntity;
import com.ssafy.withview.repository.entity.ServerEntity;
import com.ssafy.withview.repository.entity.UserEntity;
import com.ssafy.withview.repository.entity.UserServerEntity;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerServiceImpl implements ServerService {
	private final ServerRepository serverRepository;
	private final ChannelRepository channelRepository;
	private final UserServerRepository userServerRepository;
	private final UserRepository userRepository;
	private final ResourceLoader resourceLoader;
	private final AmazonS3 s3client;

	@Value(value="${cloud.aws.s3.bucket}")
	private String bucketName;

	@Value(value="${DEFAULT_IMG}")
	private String DEFAULT_IMG;

	@Override
	public List<ChannelDto> findAllChannelsByServerSeq(int seq) {
		List<ChannelEntity> entityList = channelRepository.findAllByServerSeq(seq);
		return entityList.stream().map(ChannelEntity::toDto).collect(Collectors.toList());
	}

	@Override
	public ChannelDto findChannelByName(String channelName) {
		return null;
	}

	@Transactional
	@Override
	public ServerDto insertServer(ServerDto serverDto, MultipartFile multipartFile) throws Exception{
		ServerDto result;
		try{
			if (!s3client.doesBucketExist(bucketName)) {
				s3client.createBucket(bucketName);
			}
			String originalName = "";
			File backgroundImgFile;
			String backgroundImgSearchName="";
			UUID uuid = UUID.randomUUID();
			String extend = "";
			//사진이 없는경우 로고 사진으로 대체
			if(multipartFile == null){
				originalName=DEFAULT_IMG;
			}
			//사진이 있으면 해당 사진을 배경화면으로
			else{
				originalName = multipartFile.getOriginalFilename();
			}

			extend = originalName.substring(originalName.lastIndexOf('.'));
			// #2 - 원본 파일 이름 저장
			serverDto.setBackgroundImgOriginalName(originalName);

			// #3 - 저장용 랜점 파일 이름 저장
			backgroundImgSearchName = uuid.toString()+extend;

			// #4 - 파일 임시 저장
			//파일이 있으면 임시 파일 저장
			if(multipartFile!=null){
				backgroundImgFile = new File(resourceLoader.getResource("classpath:/img/").getFile().getAbsolutePath(),backgroundImgSearchName);
				multipartFile.transferTo(backgroundImgFile);
			}else{
				backgroundImgFile = new File(resourceLoader.getResource("classpath:/img/").getFile().getAbsolutePath(),originalName);
			}
			// #5 - 이미지 서버 저장
			s3client.putObject(bucketName, "server-background/"+backgroundImgSearchName, backgroundImgFile);
			// #6 - DB 저장
			serverDto.setBackgroundImgSearchName(uuid.toString()+extend);
			ServerEntity serverEntity = ServerDto.toEntity(serverDto);
			result = ServerEntity.toDto(serverRepository.save(serverEntity));
		}catch(Exception e){
			throw new Exception("서버 생성 중 오류가 발생했습니다.");
		}

		return result;
	}

	@Transactional
	@Override
	public ServerDto updateServer(ServerDto serverDto, MultipartFile multipartFile) throws  Exception{
		ServerEntity serverEntity = serverRepository.findBySeq(serverDto.getSeq());
		System.out.println("대상 서버 " + serverEntity);
		if(serverEntity == null){
			throw new Exception("대상 서버가 없음");
		}
		if(multipartFile != null){
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
			String backgroundImgSearchName = uuid.toString()+extend;

			// #4 - 파일 임시 저장
			File backgroundImgFile = new File(resourceLoader.getResource("classpath:/img/").getFile().getAbsolutePath(),backgroundImgSearchName);
			multipartFile.transferTo(backgroundImgFile);

			// #5 - 이미지 서버 저장
			s3client.putObject(bucketName, "server-background/"+backgroundImgSearchName, backgroundImgFile);

			// #6 - DB 저장
			serverDto.setBackgroundImgSearchName(uuid.toString()+extend);
			backgroundImgFile.delete();	//기존 임시 저장용 파일 삭제
		}

		serverEntity.update(serverDto);

		return ServerEntity.toDto(serverEntity);
	}

	@Override
	public ServerDto findServerBySeq(long serverSeq) {

		return ServerEntity.toDto(serverRepository.findBySeq(serverSeq));
	}

	@Override
	public List<ServerDto> findAllServerByUserSeq(long userSeq) {
		List<ServerDto> userServerDtoList = new ArrayList<>();
		UserEntity userEntity = userRepository.findBySeq(userSeq);
		List<UserServerEntity> userServerEntityList= userServerRepository.findAllServerByUserEntity(userEntity);

		for(UserServerEntity userServerEntity : userServerEntityList){
			userServerDtoList.add(ServerEntity.toDto(userServerEntity.getServerEntity()));
		}

		return userServerDtoList;
	}

	@Transactional
	@Override
	public void deleteServer(long serverSeq,long userSeq) throws Exception{
		ServerEntity serverEntity = serverRepository.findBySeq(serverSeq);

		if(serverEntity == null){
			throw new Exception("해당 서버가 없습니다.");
		}

		if(serverEntity.getHostSeq() != userSeq){
			throw new Exception("해당 서버를 삭제할 권한이 없습니다.");
		}

		serverRepository.delete(serverEntity);
		//S3에 있는 이미지 삭제
		s3client.deleteObject(bucketName, "server-background/"+serverEntity.getBackgroundImgSearchName());

	}

	@Override
	public List<ServerDto> findAllServer() {
		List<ServerEntity> serverEntityList =serverRepository.findAll();
		List<ServerDto> serverDtoList = new ArrayList<>();

		for(int i=0;i<serverEntityList.size();i++){
			serverDtoList.add(ServerEntity.toDto(serverEntityList.get(i)));
		}
		return serverDtoList;
	}
}