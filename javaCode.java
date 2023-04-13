	/**
	 * demo1 门店数据服务(es)，门店查询列表api接口
	 **/
	public PageResult<ShopEsListRes> getShopList(ShopListRequestReq shopListRequestReq) {

		//构建es查询
		SearchExample.Builder<ShopEsIndex> builder = SearchExample.builder(ShopEsIndex.class);

		//设置size & num
		Integer size = Optional.ofNullable(shopListRequestReq.getPageSize()).orElse(10);
		Integer pageNum = Optional.ofNullable(shopListRequestReq.getPageNo()).orElse(1);
		builder.pageSize(size);
		builder.pageNum(pageNum);

		//set query
		this.setQuery(shopListRequestReq, builder);

		//set Filter
		this.setFilter(shopListRequestReq, builder);

		//set sort
		this.setSort(shopListRequestReq, builder);

		//set spatial
		this.setSpatial(shopListRequestReq, builder);

		SearchData<ShopEsIndex> searchDataResult=shopEsProxy.searchEs(builder.build());
		if(CollectionUtils.isEmpty(searchDataResult.getDocs())){
			return new PageResult<>(pageNum,size,0,new ArrayList<>());
		}

		//format set some data
		List<ShopEsListRes> shopEsListResList =this.formatShopListExtraData(searchDataResult);

		return PageResult.of(pageNum,size,searchDataResult.getTotal().intValue(), shopEsListResList);
	}



	/**
	 * demo2 异步请求获取房屋信息
	 **/
	public HdicRespVo getHdicInfoAsync(HdicReqVo hdicReqVo) {
        HdicRespVo hdicRespVo = new HdicRespVo();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        Long houseId = hdicReqVo.getHouseId();
        ArrayList<String> fields = hdicReqVo.getFields();
        if (houseId != 0) {
            // 房屋信息
            if (fields.isEmpty() || fields.contains("houseInfo")) {
                CompletableFuture<Void> futureHouse = CompletableFuture.runAsync(() -> hdicRespVo.setHouseInfo(getHouse(houseId)), executor);
                futures.add(futureHouse);
            }
            // 户型图
            if (fields.isEmpty() || fields.contains("frameImage")) {
                CompletableFuture<Void> futureFrameImage = CompletableFuture.runAsync(() -> hdicRespVo.setFrameImage(getFrameImage(houseId)), executor);
                futures.add(futureFrameImage);
            }
            // 户型信息
            if (fields.isEmpty() || fields.contains("frameInfo")) {
                CompletableFuture<Void> futureFrame = CompletableFuture.runAsync(() -> hdicRespVo.setFrameInfo(getFrame(houseId)), executor);
                futures.add(futureFrame);
            }
            // 实勘图
            if (fields.isEmpty() || fields.contains("prospectInfo")) {
                CompletableFuture<Void> futureProspect = CompletableFuture.runAsync(() -> hdicRespVo.setProspectInfo(getProspect(houseId)), executor);
                futures.add(futureProspect);
            }
        }

        Long resblockId = hdicReqVo.getResblockId();
        if (resblockId != 0) {
            // 楼盘信息
            if (fields.isEmpty() || fields.contains("resblockInfo")) {
                CompletableFuture<Void> futureResblock = CompletableFuture.runAsync(() -> hdicRespVo.setResblockInfo(getResblock(resblockId)), executor);
                futures.add(futureResblock);
            }
        }

        Long buildingId = hdicReqVo.getBuildingId();
        if (buildingId != 0) {
            // 楼栋信息
            if (fields.isEmpty() || fields.contains("buildingInfo")) {
                CompletableFuture<Void> futureBuilding = CompletableFuture.runAsync(() -> hdicRespVo.setBuildingInfo(getBuilding(buildingId)), executor);
                futures.add(futureBuilding);
            }
        }
		int size = futures.size();
        CompletableFuture
                        .allOf(futures.toArray(new CompletableFuture[size]))
                        .join();
        return hdicRespVo;
    }




    /**
     * 根据不同的企微供应商，发送企业微信机器人指令
     * */
	public void changeGroupName(ChannelRequest channelRequest) {

		ChannelFactory.getInstance(channelRequest.getChannelType()).changeGroupName(channelRequest);
	}


	public final class ChannelFactory {

		private static final Map<String, GroupService> GROUP_SERVICE_MAPS = Maps.newConcurrentMap();

		static {
			GROUP_SERVICE_MAPS.put(ChannelType.RING_LETTER.name(),
				GroupService.builder().clzz(GroupManagerDitingServiceImpl.class).desc(ChannelType.RING_LETTER.getDesc()).build());
			GROUP_SERVICE_MAPS.put(ChannelType.SIX_EARS.name(),
				GroupService.builder().clzz(GroupManagerDitingServiceImpl.class).desc(ChannelType.SIX_EARS.getDesc()).build());
			GROUP_SERVICE_MAPS.put(ChannelType.FOUNDATION.name(),
				GroupService.builder().clzz(GroupManagerServiceImpl.class).desc(ChannelType.FOUNDATION.getDesc()).build());
		}

		private ChannelFactory() {
		}

		public static GroupManagerService getInstance(String channelType) {
			final GroupService groupService = Optional.ofNullable(GROUP_SERVICE_MAPS.get(channelType)).orElseThrow(() -> new BusinessException("", "未找到通道"));
			return (GroupManagerService) SpringContextUtils.getBean(groupService.clzz);
		}

		@Data
		@Builder
		@NoArgsConstructor
		@AllArgsConstructor
		private static final class GroupService {
			private Class clzz;
			private String desc;
		}
	}
