-- 比较线程标识于锁中的标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 删除锁，也就是释放锁
    return redis.call('del', KEYS[1])
end
return 0
