package com.appnexus.bulkwarehousing.adapter.integration.inboundexecution;

import com.appnexus.bulkwarehousing.application.inboundexecution.InboundSiteCommandChannel;
import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** 单一应用写入进程 + 受信现场执行器的目录协议。执行器以命令 UUID 幂等。 */
public final class DirectoryInboundSiteCommands implements InboundSiteCommandChannel {
    private static final int MAX_BYTES=8192;
    private final Path root;
    private final boolean simulated;
    private final JsonMapper json=JsonMapper.builder().build();
    private record Notification(Job job,UUID attemptedBy,Instant attemptedAt,Instant expiresAt) {}
    private record Reply(UUID tenantId,UUID taskId,UUID commandId,String action,String state,Instant acknowledgedAt) {}

    /** @param root 访问受限的独占绝对目录，空表示关闭 @param simulated 隔离模式标识 */
    public DirectoryInboundSiteCommands(String root,boolean simulated){
        this.root=root.isBlank()?null:Path.of(root).normalize();
        if(this.root!=null&&(!this.root.isAbsolute()||!Files.isDirectory(this.root,LinkOption.NOFOLLOW_LINKS)))
            throw new IllegalArgumentException("现场指令要求已存在的私有绝对目录");
        this.simulated=simulated;
    }

    /** {@inheritDoc} */
    @Override public boolean configured(){return root!=null;}
    /** {@inheritDoc} */
    @Override public boolean simulated(){return simulated;}
    /** {@inheritDoc} */
    @Override public Job find(UUID tenant,UUID task,UUID command){
        if(!configured())return null;
        var row=read(tenant,task,command,".request.json",Notification.class);
        if(row==null)return null;
        var job=row.job();
        if(job==null||!tenant.equals(job.tenantId())||!task.equals(job.taskId())||!command.equals(job.commandId())
                ||job.action()==null||job.createdAt()==null)throw unavailable();
        return job;
    }

    /** {@inheritDoc} */
    @Override public synchronized void offer(Job job,UUID actor,Instant attemptedAt,Instant expiresAt,Instant expectedPreviousAttempt){
        var current=find(job.tenantId(),job.taskId(),job.commandId());
        if(current!=null&&current.action()!=job.action())throw new Rejection(409,"INBOUND_SITE_COMMAND_CONFLICT");
        if(expectedPreviousAttempt==null&&current!=null)return;
        if(expectedPreviousAttempt!=null&&(current==null||!attemptedAt(current).equals(expectedPreviousAttempt)
                || acknowledgement(current)!=null))return;
        var directory=root.resolve(job.tenantId().toString()).resolve(job.taskId().toString());
        try{
            if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw unavailable();
            Files.createDirectories(directory);
            if(!directory.toRealPath().equals(root.toRealPath().resolve(job.tenantId().toString()).resolve(job.taskId().toString())))
                throw unavailable();
            // 设备只消费完成原子移动的请求。重试保留原始动作、操作者和 UUID。
            var target=directory.resolve(job.commandId()+".request.json");
            var temporary=Files.createTempFile(directory,"site-",".tmp");
            try {
                Files.write(temporary,json.writeValueAsBytes(new Notification(current==null?job:current,actor,attemptedAt,expiresAt)));
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temporary);}
        }catch(IOException ex){throw unavailable();}
    }
    /** {@inheritDoc} */
    @Override public Instant attemptedAt(Job job){
        var record=read(job.tenantId(),job.taskId(),job.commandId(),".request.json",Notification.class);
        if(record==null||record.attemptedAt()==null)throw unavailable();
        return record.attemptedAt();
    }
    /** {@inheritDoc} */
    @Override public Acknowledgement acknowledgement(Job job){
        var ack=read(job.tenantId(),job.taskId(),job.commandId(),".ack.json",Reply.class);
        if(ack==null||!job.tenantId().equals(ack.tenantId())||!job.taskId().equals(ack.taskId())
                ||!job.commandId().equals(ack.commandId())||!job.action().name().equals(ack.action())
                ||ack.acknowledgedAt()==null||ack.acknowledgedAt().isBefore(job.createdAt()))return null;
        if(!"SUCCEEDED".equals(ack.state())&&!"FAILED".equals(ack.state()))return null;
        return new Acknowledgement(ack.state(),ack.acknowledgedAt());
    }

    private <T>T read(UUID tenant,UUID task,UUID command,String suffix,Class<T> type){
        var dir=root.resolve(tenant.toString()).resolve(task.toString());
        var file=dir.resolve(command+suffix);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return null;
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            if(!dir.toRealPath().equals(root.toRealPath().resolve(tenant.toString()).resolve(task.toString())))throw unavailable();
            var bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw unavailable();
            return json.readValue(bytes,type);
        }catch(IOException ex){throw unavailable();}
    }
    private static Rejection unavailable(){return new Rejection(503,"INBOUND_SITE_CHANNEL_UNAVAILABLE");}
}
