package com.ssafy.bab.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ssafy.bab.dao.CardRfidDao;
import com.ssafy.bab.dao.ContributionDao;
import com.ssafy.bab.dao.ContributionOldDao;
import com.ssafy.bab.dao.ContributorDao;
import com.ssafy.bab.dao.ItemDao;
import com.ssafy.bab.dao.OrderDao;
import com.ssafy.bab.dao.PaymentDao;
import com.ssafy.bab.dao.PaymentGdreamDao;
import com.ssafy.bab.dao.StoreDao;
import com.ssafy.bab.dao.StoreVariablesDao;
import com.ssafy.bab.dao.UserDao;
import com.ssafy.bab.dto.CPaymentInfo;
import com.ssafy.bab.dto.CardRfid;
import com.ssafy.bab.dto.Contribution;
import com.ssafy.bab.dto.ContributionOld;
import com.ssafy.bab.dto.Contributor;
import com.ssafy.bab.dto.GPaymentInfo;
import com.ssafy.bab.dto.GdreamResult;
import com.ssafy.bab.dto.IPaymentInfo;
import com.ssafy.bab.dto.Item;
import com.ssafy.bab.dto.ItemAndCount;
import com.ssafy.bab.dto.Msg;
import com.ssafy.bab.dto.NPaymentInfo;
import com.ssafy.bab.dto.Orders;
import com.ssafy.bab.dto.Payment;
import com.ssafy.bab.dto.PaymentGdream;
import com.ssafy.bab.dto.PaymentItem;
import com.ssafy.bab.dto.StoreVariables;
import com.ssafy.bab.dto.User;

import net.nurigo.java_sdk.api.Message;
import net.nurigo.java_sdk.exceptions.CoolsmsException;

@Service
public class PaymentServiceImpl implements PaymentService {
	
	@Value("${coolsms.API_KEY}")
	private String API_KEY;
	
	@Value("${coolsms.API_SECRET}")
	private String API_SECRET;
	
	@Value("${coolsms.PHONE}")
	private String PHONE;
	
	@Value("${Kakao.WEB_RETURN_URL}")
	private String URL;
	
	PaymentGdream paymentG = null;
	
	@Autowired
	PaymentDao paymentDao;
	
	@Autowired
	PaymentGdreamDao paymentGDao;
	
	@Autowired
	ContributionDao contributionDao;
	
	@Autowired
	ContributionOldDao contributionOldDao;
	
	@Autowired
	ItemDao itemDao;
	
	@Autowired
	StoreDao storeDao;
	
	@Autowired
	UserDao userDao;
	
	@Autowired
	ContributorDao contributorDao;
	
	@Autowired
	StoreVariablesDao storeVariablesDao;
	
	@Autowired
	OrderDao orderDao;
	
	@Autowired
	CardRfidDao cardRfidDao;

	@Override
	public String checkNaverPayTransaction(NPaymentInfo paymentInfo) throws ParseException {
		
		// itemCount = 0 ??? ?????? ????????????
		for (int i = 0; i < paymentInfo.getItemList().size(); i++) {
			if (paymentInfo.getItemList().get(i).getItemCount() <= 0) return "Check ItemCount";
			if(itemDao.findByItemIdAndStoreId(paymentInfo.getItemList().get(i).getItemId(), paymentInfo.getItemList().get(i).getStoreId()) == null) return "Check itemId or storeId";
		}
		
		// String -> Date
		SimpleDateFormat transFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		Date tradeConfirmYmdt = transFormat.parse(paymentInfo.getTradeConfirmYmdt());
		
		// ????????????
		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
		String wdate = vans.format(tradeConfirmYmdt);
		
		//payment ????????? ????????????
		Payment payment = new Payment();
		payment.setPaymentId(wdate);
		payment.setPaymentAmount(paymentInfo.getTotalPayAmount());
		payment.setPaymentDate(tradeConfirmYmdt);
		payment.setNaverpayMerchantId(paymentInfo.getMerchantId());
		payment.setNaverpayPaymentId(paymentInfo.getPaymentId());
		paymentDao.save(payment);
		
		// ?????? ????????? ?????? user??? ?????? ?????? ????????? ????????? (??? ?????? ????????? null)
		User user = userDao.findByUserSeq(paymentInfo.getUserSeq());
		
		// ????????? ????????? ?????? contributor??? ?????? ????????? ????????? ????????? (??? ?????? ????????? null)
		Contributor contributor = null;
		if(paymentInfo.getUserSeq() == -1 && paymentInfo.getContributorPhone() != null) {
			contributor = contributorDao.findByContributorPhone(paymentInfo.getContributorPhone());
			if(contributor == null) {
				contributor = new Contributor();
            	contributor.setContributorPhone(paymentInfo.getContributorPhone());
            	contributorDao.save(contributor);
			}
		}
		
		int totalSupportPrice = payment.getPaymentAmount();
		int totalSupportItem = paymentInfo.getItemList().size();
		
		for(PaymentItem paymentItem : paymentInfo.getItemList()) {
			Item item = itemDao.findByItemIdAndStoreId(paymentItem.getItemId(), paymentItem.getStoreId());
			if(item == null) throw new RuntimeException();
			for(int i = 0; i < paymentItem.getItemCount(); i++) {
				// Contribution ????????? ????????????
				Contribution contribution = new Contribution();
				contribution.setItemId(paymentItem.getItemId());
				contribution.setStoreId(paymentItem.getStoreId());
				if(user != null) contribution.setUser(user);
				else if(contributor != null) contribution.setContributor(contributor);
				contribution.setContributionDate(tradeConfirmYmdt);
				contribution.setContributionUse(0);
				contribution.setPayment(payment);
				contribution.setContributionMessage(paymentItem.getMsg());
				contributionDao.save(contribution);
			
			}
			// item ????????? ????????????
			item.setItemAvailable(item.getItemAvailable() + paymentItem.getItemCount());
			item.setItemTotal(item.getItemTotal() + paymentItem.getItemCount());
			itemDao.save(item);
		}
		
		// storeVariables, user ????????? ????????????
        if(totalSupportItem != 0) {
        	StoreVariables storeVariables = storeVariablesDao.findByStoreId(paymentInfo.getItemList().get(0).getStoreId());
        	storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() + totalSupportItem);
        	storeVariables.setStoreItemTotal(storeVariables.getStoreItemTotal() + totalSupportItem);
        	storeVariables.setStoreTotalContributionAmount(storeVariables.getStoreTotalContributionAmount() + totalSupportPrice);
        	storeVariablesDao.save(storeVariables);
        	
        	if(user != null) {
        		user.setUserTotalContributionAmount(user.getUserTotalContributionAmount() + totalSupportPrice);
        		user.setUserTotalContributionCount(user.getUserTotalContributionCount() + totalSupportItem);
        		userDao.save(user);
        	}
        }

		return "SUCCESS";
	}

	@Override
	public String checkIamPortTransaction(IPaymentInfo paymentInfo) {
		
		// itemCount = 0 ??? ?????? ????????????
		for (int i = 0; i < paymentInfo.getItemList().size(); i++) {
			if (paymentInfo.getItemList().get(i).getItemCount() <= 0) return "Check ItemCount";
			if(itemDao.findByItemIdAndStoreId(paymentInfo.getItemList().get(i).getItemId(), paymentInfo.getItemList().get(i).getStoreId()) == null) return "Check itemId or storeId";
		}
		
		// UNIX timestamp -> Date
		Date date = new java.util.Date(paymentInfo.getPaid_at()*1000L); 

		// ????????????
		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
		String wdate = vans.format(date);

		// payment ????????? ????????????
		Payment payment = new Payment();
		payment.setPaymentId(wdate);
		payment.setPaymentAmount(paymentInfo.getPaid_amount());
		payment.setPaymentDate(date);
		payment.setImpMerchantId(paymentInfo.getMerchant_uid());
		payment.setImpUid(paymentInfo.getImp_uid());
		paymentDao.save(payment);

		// ?????? ????????? ?????? user??? ?????? ?????? ????????? ????????? (??? ?????? ????????? null)
		User user = userDao.findByUserSeq(paymentInfo.getUserSeq());

		// ????????? ????????? ?????? contributor??? ?????? ????????? ????????? ????????? (??? ?????? ????????? null)
		Contributor contributor = null;
		if(paymentInfo.getUserSeq() == -1 && paymentInfo.getContributorPhone() != null) {
			contributor = contributorDao.findByContributorPhone(paymentInfo.getContributorPhone());
			if(contributor == null) {
				contributor = new Contributor();
            	contributor.setContributorPhone(paymentInfo.getContributorPhone());
            	contributorDao.save(contributor);
			}
		}

		int totalSupportPrice = 0;
		int totalSupportItem = 0;

		for (PaymentItem paymentItem : paymentInfo.getItemList()) {
			Item item = itemDao.findByItemIdAndStoreId(paymentItem.getItemId(), paymentItem.getStoreId());
			for (int i = 0; i < paymentItem.getItemCount(); i++) {
				// Contribution ????????? ????????????
				Contribution contribution = new Contribution();
				contribution.setItemId(paymentItem.getItemId());
				contribution.setStoreId(paymentItem.getStoreId());
				if (user != null)
					contribution.setUser(user);
				else if (contributor != null)
					contribution.setContributor(contributor);
				contribution.setContributionDate(date);
				contribution.setContributionUse(0);
				contribution.setPayment(payment);
				contribution.setContributionMessage(paymentItem.getMsg());
				contributionDao.save(contribution);

			}
			// item ????????? ????????????
			item.setItemAvailable(item.getItemAvailable() + paymentItem.getItemCount());
			item.setItemTotal(item.getItemTotal() + paymentItem.getItemCount());
			itemDao.save(item);
			
			// for storeVariables, user ????????? ????????????  
			totalSupportPrice += item.getSupportPrice() * paymentItem.getItemCount();
			totalSupportItem += paymentItem.getItemCount();
		}

		// storeVariables, user ????????? ????????????
		if (totalSupportItem != 0) {
			StoreVariables storeVariables = storeVariablesDao
					.findByStoreId(paymentInfo.getItemList().get(0).getStoreId());
			storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() + totalSupportItem);
			storeVariables.setStoreItemTotal(storeVariables.getStoreItemTotal() + totalSupportItem);
			storeVariables.setStoreTotalContributionAmount(
					storeVariables.getStoreTotalContributionAmount() + totalSupportPrice);
			storeVariablesDao.save(storeVariables);

			if (user != null) {
				user.setUserTotalContributionAmount(user.getUserTotalContributionAmount() + totalSupportPrice);
				user.setUserTotalContributionCount(user.getUserTotalContributionCount() + totalSupportItem);
				userDao.save(user);
			}
		}

		return "SUCCESS";
	}

	@Override
	public String checkCreditCardTransaction(CPaymentInfo paymentInfo) throws ParseException {
		
		// itemCount = 0 ??? ?????? ????????????
		for (int i = 0; i < paymentInfo.getItemList().size(); i++) {
			if (paymentInfo.getItemList().get(i).getItemCount() <= 0) return "Check ItemCount";
			if(itemDao.findByItemIdAndStoreId(paymentInfo.getItemList().get(i).getItemId(), paymentInfo.getItemList().get(i).getStoreId()) == null) return "Check itemId or storeId";

		}
		
		// ????????????
		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.HOUR, 9);
		String wdate = vans.format(new Date());

		// ????????????
		Date tradeConfirmYmdt = vans.parse(paymentInfo.getPaidAt());
		cal.setTime(tradeConfirmYmdt);
		cal.add(Calendar.HOUR, -9);
		tradeConfirmYmdt = cal.getTime();
		
		/*
         * ******* DB ????????? ???????????? *******
         */
		
		// payment ????????? ????????????
		Payment payment = new Payment();
        payment.setPaymentId(wdate);
        payment.setPaymentDate(tradeConfirmYmdt);
        payment.setPaymentAmount(paymentInfo.getTotalAmount());
        payment.setCreditApprovalNumber(paymentInfo.getApprovalNumber());
        payment.setCreditStoreId(paymentInfo.getItemList().get(0).getStoreId());
        paymentDao.save(payment);
        
        User user = null;
        Contributor contributor = null;
        
        // ????????? ????????? ????????? ?????? ?????? ??????/????????? ????????? ??????????????? ????????? ????????? ?????? ???
        if(paymentInfo.getContributorPhone() != null && !paymentInfo.getContributorPhone().equals("")) {
        	user = userDao.findByUserPhone(paymentInfo.getContributorPhone());
        	contributor = contributorDao.findByContributorPhone(paymentInfo.getContributorPhone());
        	if(user == null && contributor == null){
        		contributor = new Contributor();
        		contributor.setContributorPhone(paymentInfo.getContributorPhone());
        		contributorDao.save(contributor);
        	}
        }
        
        int totalSupportPrice = 0;
        int totalSupportItem = 0;
        
     // order, contribution ????????? ????????????
        for (PaymentItem paymentItem : paymentInfo.getItemList()) {
        	Item item = itemDao.findByItemIdAndStoreId(paymentItem.getItemId(), paymentItem.getStoreId());
			if(paymentItem.getSupport() == 1) {
				for(int i = 0; i < paymentItem.getItemCount(); i++) {
					// Contribution ????????? ????????????
					Contribution contribution = new Contribution();
					contribution.setItemId(paymentItem.getItemId());
					contribution.setStoreId(paymentItem.getStoreId());
					if(user != null) contribution.setUser(user);
					else if(contributor != null) contribution.setContributor(contributor);
					contribution.setContributionDate(tradeConfirmYmdt);
					contribution.setContributionUse(0);
					contribution.setPayment(payment);
					contribution.setContributionMessage(paymentItem.getMsg());
					contributionDao.save(contribution);
				
				}
				// item ????????? ????????????
				item.setItemAvailable(item.getItemAvailable() + paymentItem.getItemCount());
				item.setItemTotal(item.getItemTotal() + paymentItem.getItemCount());
				itemDao.save(item);
				
				// for storeVariables, user ????????? ????????????  
				totalSupportPrice += paymentItem.getItemPrice() * paymentItem.getItemCount();
				totalSupportItem += paymentItem.getItemCount();
			}else {
				Orders order = new Orders();
				order.setItemId(paymentItem.getItemId());
				order.setStoreId(paymentItem.getStoreId());
				order.setOrderDate(tradeConfirmYmdt);
				order.setOrderCount(paymentItem.getItemCount());
				order.setPayment(payment);
				
//				!!!!!!!!!!!!orderDone ????????????!!!!!!!!!!!!!!!!
//				order.setOrderDone(tradeConfirmYmdt);
				orderDao.save(order);
			}
		}
        
        
     // storeVariables, user ????????? ????????????
        if(totalSupportItem != 0) {
        	StoreVariables storeVariables = storeVariablesDao.findByStoreId(paymentInfo.getItemList().get(0).getStoreId());
        	storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() + totalSupportItem);
        	storeVariables.setStoreItemTotal(storeVariables.getStoreItemTotal() + totalSupportItem);
        	storeVariables.setStoreTotalContributionAmount(storeVariables.getStoreTotalContributionAmount() + totalSupportPrice);
        	storeVariablesDao.save(storeVariables);
        	
        	if(user != null) {
        		user.setUserTotalContributionAmount(user.getUserTotalContributionAmount() + totalSupportPrice);
        		user.setUserTotalContributionCount(user.getUserTotalContributionCount() + totalSupportItem);
        		userDao.save(user);
        	}
        }
        
        
		return payment.getPaymentId();
	}

	@Override
	public GdreamResult checkGDreamTransaction(GPaymentInfo paymentInfo) throws ParseException {
		
		// itemCount = 0 ??? ?????? ????????????
		for (int i = 0; i < paymentInfo.getItemList().size(); i++) {
			if (paymentInfo.getItemList().get(i).getItemCount() <= 0) {
				System.out.println("Check ItemCount");
				return null;
			}
		}
		
		if(storeDao.findByStoreId(paymentInfo.getItemList().get(0).getStoreId()) == null) {
			System.out.println("Invalid Store");
			return null;
		}

		GdreamResult result = new GdreamResult();
		
//		// ????????????
//		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
//		String wdate = vans.format(new Date());
//
//		// ????????????
//		Date tradeConfirmYmdt = vans.parse(paymentInfo.getPaidAt());
		
		// ????????????
		SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.HOUR, 9);
		String wdate = vans.format(new Date());
		
		// ????????????
		Date tradeConfirmYmdt = vans.parse(paymentInfo.getPaidAt());
		cal.setTime(tradeConfirmYmdt);
		cal.add(Calendar.HOUR, -9);
		tradeConfirmYmdt = cal.getTime();
		
		
		/*
         * ******* DB ????????? ???????????? *******
         */
		
		// PaymentGdream ????????? ????????????
		paymentG = new PaymentGdream();
		paymentG.setPaymentGdreamId(wdate);
		paymentG.setPaymentGdreamDate(tradeConfirmYmdt);
		paymentG.setPaymentGdreamAmount(paymentInfo.getTotalGDreamAmount());
		paymentG.setPaymentGdreamApproval(paymentInfo.getGDreamApproval());
		paymentG.setPaymentGdreamStoreId(paymentInfo.getItemList().get(0).getStoreId());
		paymentGDao.save(paymentG);
		
		// ???????????? ??????
		int totalPrice = 0;
		int supportPrice = 0;
		String storeName = storeDao.findByStoreId(paymentInfo.getItemList().get(0).getStoreId()).getStoreName();
		ArrayList<Contribution> contributionList = null;
		ArrayList<ContributionOld> contributionOldList = null;
		ArrayList<Msg> msgList = new ArrayList<>();
		ArrayList<ItemAndCount> updateList = new ArrayList<>(); 
		try {
			for (PaymentItem paymentItem : paymentInfo.getItemList()) {
				// ????????? ?????? ????????? ??????????????? ??????
				Item item = itemDao.findByItemIdAndStoreId(paymentItem.getItemId(), paymentItem.getStoreId());
				if(item == null) throw new RuntimeException();
				// ?????? ?????? ?????? ??????
				totalPrice += paymentItem.getItemCount() * item.getItemPrice();
				
				// ????????? ??????????????? ?????????????????? ????????? ??????
				contributionList = null;
				contributionList = contributionDao.findByStoreIdAndItemIdAndContributionUseOrderByContributionDate(item.getStoreId(), item.getItemId(), 0);
				if(contributionList != null) {
					int size = contributionList.size();
					int i = 0;
			    	for (i = 0; i < paymentItem.getItemCount() && i < size ; i++) {

						// Contribution ????????? ????????????
						Contribution contribution = contributionList.get(i);
						contribution.setContributionDateUsed(tradeConfirmYmdt);
						contribution.setContributionUse(1);
						contribution.setPaymentGdream(paymentG);
						contribution.setContributionAnswer(paymentItem.getMsg());
						contributionDao.save(contribution);
						
						result.setContributionMsg(contribution.getContributionMessage());
						
						// ???????????? ?????? ??????
						supportPrice += item.getSupportPrice();

						// ?????? ????????? ????????? ????????? ??????
						String phone = null;
						if(contribution.getContributor() != null && contribution.getContributor().getContributorPhone() != null) phone = contribution.getContributor().getContributorPhone();
						else if (contribution.getUser() != null && contribution.getUser().getUserPhone() != "temp") phone = contribution.getUser().getUserPhone();
						
						// ?????? ?????? ???????????? ??????
						if (phone != null) {
							Msg msg = new Msg();
							msg.setItemName(item.getItemName());
							msg.setStoreName(storeName);
							msg.setPhone(phone);
							msgList.add(msg);
						}

					}

			    	// Item, StoreVariables ????????? ???????????? ???????????? ??????
			    	int count = Math.min(paymentItem.getItemCount(), size);
			    	
			    	ItemAndCount iac = new ItemAndCount();
			    	iac.setCount(count);
			    	iac.setItem(item);
			    	
			    	updateList.add(iac);
				}else {
					// ????????? ??????????????? ???????????? ?????????????????? ????????? ??????
					contributionOldList = null;
					contributionOldList = contributionOldDao.findByStoreIdAndItemIdAndContributionUseOrderByContributionDate(item.getStoreId(), item.getItemId(), 0);
					if(contributionOldList != null) {
						int size = contributionOldList.size();
						int i = 0;
				    	for (i = 0; i < paymentItem.getItemCount() && i < size ; i++) {

							// ContributionOld ????????? ????????????
							ContributionOld contributionOld = contributionOldList.get(i);
							contributionOld.setContributionDateUsed(tradeConfirmYmdt);
							contributionOld.setContributionUse(1);
							contributionOld.setPaymentGdream(paymentG);
							contributionOld.setContributionAnswer(paymentItem.getMsg());
							contributionOldDao.save(contributionOld);
							
							// ???????????? ?????? ??????
							supportPrice += item.getSupportPrice();

							// ?????? ????????? ????????? ????????? ??????
							String phone = null;
							if(contributionOld.getContributor() != null && contributionOld.getContributor().getContributorPhone() != null) phone = contributionOld.getContributor().getContributorPhone();
							else if (contributionOld.getUser() != null && contributionOld.getUser().getUserPhone() != "temp") phone = contributionOld.getUser().getUserPhone();
							
							// ?????? ?????? ???????????? ??????
							if (phone != null) {
								Msg msg = new Msg();
								msg.setItemName(item.getItemName());
								msg.setStoreName(storeName);
								msg.setPhone(phone);
								msgList.add(msg);
							}

						}

				    	// Item, StoreVariables ????????? ???????????? ???????????? ??????
				    	int count = Math.min(paymentItem.getItemCount(), size);
				    	
				    	ItemAndCount iac = new ItemAndCount();
				    	iac.setCount(count);
				    	iac.setItem(item);
				    	
				    	updateList.add(iac);
					}
				}

				Orders order = new Orders();
				order.setItemId(paymentItem.getItemId());
				order.setStoreId(paymentItem.getStoreId());
				order.setOrderDate(tradeConfirmYmdt);
				order.setOrderCount(paymentItem.getItemCount());
				order.setPaymentGdream(paymentG);

//				!!!!!!!!!!!!orderDone ????????????!!!!!!!!!!!!!!!!
//				order.setOrderDone(tradeConfirmYmdt);
				orderDao.save(order);
				
			}
			
			if(totalPrice != supportPrice + paymentInfo.getTotalGDreamAmount()) {
				System.out.println("????????????????????? ?????? + ?????? ?????? ?????? != ??? ????????? ?????? ??????");
				throw new Exception();
			}
			
		} catch (Exception e) {
			
			// contribution ????????? ???????????? ??????
			ArrayList<Contribution> deleteList = contributionDao.findByPaymentGdream_paymentGdreamId(paymentG.getPaymentGdreamId());
			for (Contribution contribution : deleteList) {
				contribution.setContributionDateUsed(null);
				contribution.setContributionUse(0);
				contribution.setPaymentGdream(null);
				contribution.setContributionAnswer(null);
				contributionDao.save(contribution);
			}
			
			// contributionOld ????????? ???????????? ??????
			ArrayList<ContributionOld> deleteList2 = contributionOldDao.findByPaymentGdream_paymentGdreamId(paymentG.getPaymentGdreamId());
			for (ContributionOld contributionOld : deleteList2) {
				contributionOld.setContributionDateUsed(null);
				contributionOld.setContributionUse(0);
				contributionOld.setPaymentGdream(null);
				contributionOld.setContributionAnswer(null);
				contributionOldDao.save(contributionOld);
			}
			
			// order ????????? ???????????? ??????
			ArrayList<Orders> deleteList3 = orderDao.findByPaymentGdream_paymentGdreamId(paymentG.getPaymentGdreamId());
			for (Orders orders : deleteList3) {
				orderDao.delete(orders);
			}
			
			// paymengGdream ????????? ???????????? ??????
			paymentGDao.delete(paymentG);
			
			return null;
		}
		
		// ?????? ??????
		for (Msg msg : msgList) {
//			System.out.println(msg.getItemName() + " " + msg.getStoreName());
			Message coolsms = new Message(API_KEY, API_SECRET);

			// 4 params(to, from, type, text) are mandatory. must be filled
			HashMap<String, String> params = new HashMap<String, String>();
			params.put("to", msg.getPhone()); // ??????????????????
			params.put("from", PHONE); // ??????????????????. ?????????????????? ??????,?????? ?????? ?????? ????????? ?????? ???
//	        	params.put("from", "01011111111");  // ??????????????????. ?????????????????? ??????,?????? ?????? ?????? ????????? ?????? ???
			params.put("type", "LMS"); // SMS, LMS
			params.put("text", "'????????????'?????? ????????? ?????? '" + msg.getItemName() + "'???(???) ?????? ?????????????????????. "
					+ "????????? ???????????? ????????? ?????????????????? ???????????????.\n\n"
					+ "????????? ????????? ???????????? ?????????????????? ??????????????????.\n" + URL + "\n\n????????????????????????: 080-600-5653");

			try {
				JSONObject obj = (JSONObject) coolsms.send(params);
				System.out.println(obj.toString());
			} catch (CoolsmsException e) {
				System.out.println(e.getMessage());
				System.out.println(e.getCode());
			}

			
	        
		}
		
		// item, storeVariables ????????? ????????????
		for (ItemAndCount itemAndCount : updateList) {
			
			Item item = itemAndCount.getItem();
			int count = itemAndCount.getCount();
			
			// item ????????? ????????????
			item.setItemAvailable(item.getItemAvailable() - count);
			itemDao.save(item);
			
			// storeVariables ????????? ????????????
			StoreVariables storeVariables = storeVariablesDao.findByStoreId(item.getStoreId());
			storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() - count);
			storeVariablesDao.save(storeVariables);
		}

		
		result.setPaymentId(paymentG.getPaymentGdreamId());
		return result;
	}

	@Override
	public String sendMsg() {
		String api_key = API_KEY;
        String api_secret = API_SECRET;
        Message coolsms = new Message(api_key, api_secret);
        List<Integer> contributionList = contributionDao.getMaxContributionId();
        System.out.println(PHONE);
        for (Integer contributionId : contributionList) {
        	
			Contribution contribution = contributionDao.findByContributionId(contributionId);
			if (contribution.getUser() == null || contribution.getUser().getUserPhone() == "temp" || contribution.getUser().getUserSeq() != 3) continue;
			
			Item item = itemDao.findByItemIdAndStoreId(contribution.getItemId(), contribution.getStoreId());
			
			// ????????????
			SimpleDateFormat vans = new SimpleDateFormat("yyyyMMdd-HHmmss");
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			cal.add(Calendar.HOUR, 9);
			String wdate = vans.format(cal.getTime());

			// ????????????
			cal.add(Calendar.SECOND, -10);
			Date tradeConfirmYmdt = cal.getTime();
			
			// PaymentGdream ????????? ????????????
			paymentG = new PaymentGdream();
			paymentG.setPaymentGdreamId(wdate);
			paymentG.setPaymentGdreamDate(tradeConfirmYmdt);
			paymentG.setPaymentGdreamAmount(item.getItemPrice() - item.getSupportPrice());
			paymentG.setPaymentGdreamApproval("gdream_" + wdate);
			paymentG.setPaymentGdreamStoreId(item.getStoreId());
			paymentGDao.save(paymentG);
			
			// Contribution ????????? ????????????
			contribution.setContributionDateUsed(tradeConfirmYmdt);
			contribution.setContributionUse(1);
			contribution.setPaymentGdream(paymentG);
			contribution.setContributionAnswer("????????? ??? ??????????????? (?????`???????)");
			contributionDao.save(contribution);
			
			// Item ????????? ????????????
			item.setItemAvailable(item.getItemAvailable() - 1);
			itemDao.save(item);
			
			// storeVariables ????????? ????????????
			StoreVariables storeVariables = storeVariablesDao.findByStoreId(item.getStoreId());
			storeVariables.setStoreItemAvailable(storeVariables.getStoreItemAvailable() - 1);
			storeVariablesDao.save(storeVariables);
			
			Orders order = new Orders();
			order.setItemId(item.getItemId());
			order.setStoreId(item.getStoreId());
			order.setOrderDate(tradeConfirmYmdt);
			order.setOrderCount(1);
			order.setPaymentGdream(paymentG);
			
			// 4 params(to, from, type, text) are mandatory. must be filled
	        HashMap<String, String> params = new HashMap<String, String>();
	        params.put("to", contribution.getUser().getUserPhone());    // ??????????????????
	        params.put("from", PHONE);    // ??????????????????. ?????????????????? ??????,?????? ?????? ?????? ????????? ?????? ???
	        params.put("type", "LMS");
	        params.put("text", "'????????????'?????? ????????? ?????? '" + item.getItemName() + "'???(???) ?????? ?????????????????????. "
	    					+ "????????? ???????????? ????????? ?????????????????? ???????????????.\n\n"
	    					+ "????????? ????????? ???????????? ?????????????????? ??????????????????.\n" + URL + "\n\n????????????????????????: 080-600-5653");

	        System.out.println("================================");
	        System.out.println(contributionId);
	        System.out.println(params.get("to"));
	        System.out.println(params.get("text"));
	        
	        try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	        
	        try {
	            JSONObject obj = (JSONObject) coolsms.send(params);
	            System.out.println(obj.toString());
//	            return "SUCCESS";
	        } catch (CoolsmsException e) {
	            System.out.println(e.getMessage());
	            System.out.println(e.getCode());
	        }
			
		}
        
        return "SUCCESS";
	}

	@Override
	public String getRFIDCardType(String cardNumber) {
		CardRfid result = cardRfidDao.findByCardNumber(cardNumber);
		if(result == null) return "Invalid CardNumber";
		return result.getCardType();
	}

	@Override
	public String createRfidCard(String cardNumber, String cardType) {
		if(cardNumber == null || cardType == null) return "Check cardNumber and cardType";
		if(cardRfidDao.findByCardNumber(cardNumber) != null) return "Already Registered Card";
		if("credit".equals(cardType) || "gdream".equals(cardType)) {
			CardRfid card = new CardRfid();
			card.setCardNumber(cardNumber);
			card.setCardType(cardType);
			cardRfidDao.save(card);
			return "SUCCESS";
		}
		return "Check cardType";
	}

}
