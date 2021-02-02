package com.ssafy.bab.dto;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// jpa default 설정이 java에서 camelcase로 되어있는 변수명, 테이블명 등등등을
// underscore로 바꾸어 mysql의 변수명과 맞춰짐 
// storeId <-> store_id
@Getter
@Setter
@ToString
@Entity
public class Store implements Serializable{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int storeId;
	private String storeName;
	private String storeLocation;
	private String storeCategory;
	
	//@JsonFormat(shape = JsonFormat.Shape.STRING, pattern="2021-01-01 00:00:00")
	private Timestamp storeRegDate;
	private String storePhone;
	private int storeKiosk;

	@ManyToOne
	@JoinColumn(name="location_id")
	private Location location;
	
//	양방향일 경우 추가
//	storevariablesdto 엔티티에 있는 storeDto와 매핑
//	@OneToOne(mappedBy ="storeDto")
//	private StoreVariablesDto storeVariablesDto;

}
