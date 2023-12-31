package com.ssafy.withview.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.withview.entity.ServerEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ServerDto {
	private Long seq;
	private String name;
	@Builder.Default
	private Integer limitChannel = 5;
	private Long hostSeq;
	private String backgroundImgSearchName;
	private String backgroundImgOriginalName;
	private Boolean isFavorite;
	@Builder.Default
	private Integer peopleCnt = 0;

	public static ServerEntity toEntity(ServerDto serverDto) {
		if (serverDto == null) {
			return null;
		}
		return ServerEntity.builder()
			.name(serverDto.getName())
			.limitChannel(serverDto.getLimitChannel())
			.backgroundImgSearchName(serverDto.getBackgroundImgSearchName())
			.backgroundImgOriginalName(serverDto.getBackgroundImgOriginalName())
			.hostSeq(serverDto.getHostSeq())
			.build();
	}

	public String toJson() {
		String json = null;
		try {
			json = new ObjectMapper().writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		return json;
	}
}