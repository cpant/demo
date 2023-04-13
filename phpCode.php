<?php
 /**
  * demo1 交易单人员点单提交
  **/
 public function commit($businessId, $role2ucIdMap, $taskDefinitionKey, $term = App::TERM_PC)
    {
        $this->businessUpdateCoreService->checkWeibaoOperation($businessId, null, $taskDefinitionKey);
        //点单权限校验
        $this->checkAuthDianDanUser($businessId);
        //点单交易单状态校验
        $this->checkBusinessStatus($taskDefinitionKey, $businessId);

        \DB::beginTransaction();
        try {
            $this->updateWeiBaoDianDanBitStatus($businessId, $taskDefinitionKey, $term);

            $params = ['businessId' => $businessId, 'role2ucIdMap' => $role2ucIdMap];
            //点单提交人员变更函数
            // 注意！底层调用获取待办的方法，涉及到外部调用，存在隐患
            $this->participantCoreService->changeParticipantStatus(Participant::DIAN_DAN_COMMIT,$params);

        } catch (\Exception $e) {
            \DB::rollBack();
            throw $e;
        }
        \DB::commit();

        $this->updateDianDanStatusInCache($businessId);

        dispatch(new IndexUpdateJob($businessId))->delay(null);

        return true;
    }


 /**
  * demo2 根据不同事件发送制定消息
  **/
  public function handle(BaseEvent $event)
    {
        $this->initAppId($event);
        $businessId = $event->getBusinessId();
        $businessVo = $this->smsService->getJobBusinessVo($businessId);
        $config     = config('ntsToGroupAbilityActionMap');
        $eventName  = $this->getEventName($event);
        $content    = $this->getPushData($businessVo, $config, $eventName);
        if (empty($content)) {
           return;
        }
        \Log::info(sprintf("NotifyGroupAbilityListener businessId：%s event:%s content:%s", $businessId, get_class($event),
            json_encode($content)));

        $this->kafkaPushNotifyGroupAbilityService->notifyGroupAbility($businessVo->id, $content);

    }

    private function getEventName(BaseEvent $event)
    {
        if ($event instanceof NotifyGroupAbilityEvent) {
            return $event->eventName;
        } else {
            return $this->groupAbilityService->getClassName($event);
        }
    }

    public function getPushData($businessVo, $config, $eventName)
    {
        $ret = [];
        if (!isset($config[$eventName]) || !isset($config[$eventName]['action']) || !isset($config[$eventName]['messageArr'])) {
            return $ret;
        }
        $messageArr = $this->getMessages($businessVo,$config[$eventName]['messageArr']);
        return $this->groupAbilityService->getPushGroupAbilityData($businessVo->contractId, $businessVo->businessCode, $messageArr,
            $config[$eventName]['action'], $businessVo->appId, $businessVo->tradeProduct, $businessVo->subTradeProduct);


    }

    public function getMessages($businessVo, $messageArr)
    {
        $messages = [];
        $this->smsService->setParticipants($businessVo);
        foreach ($messageArr as $messageConfig) {
            if (!isset($messageConfig['messageCode']) || !isset($messageConfig['params'])) {
                continue;
            }
            $params     = $this->getMessageParams($businessVo, $messageConfig['params']);
            $messages[] = ['messageCode' => $messageConfig['messageCode'], 'params' => $params];
        }

        return $messages;

    }

    private function getMessageParams($businessVo, $expressions)
    {
        $ret = [];
        if (empty($expressions)) {
            return $ret;
        }
        $expressionLanguage = new ExpressionLanguage();
        foreach ($expressions as $key => $expression) {
            try {
                $value = $expressionLanguage->evaluate($expression, [
                    "BUSINESS"          => $businessVo,
                    "SMS_SERVICE"       => $this->smsService,
                    "GROUP_CARD_CONFIG" => GroupConfigs::$groupCardConfig,
                ]);
                if ($value !== false) {
                    $ret[$key] = ['index' => $key, 'value' => $value];
                } else {
                    \Log::warning(sprintf("推送群聊服务数据解析失败,businessId:[%s]，表达式：%s", $businessVo->id, json_encode($expression)));
                }
            } catch (\Throwable $exception) {
                unset($ret[$key]);
                \Log::warning(sprintf("推送群聊服务数据解析失败,businessId:[%s]，表达式：%s，报错信息： %s", $businessVo->id, json_encode($expression),
                    $exception->getMessage()));
            }
        }

        return $ret;
    }
