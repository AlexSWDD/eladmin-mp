/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import me.zhengjie.config.FileProperties;
import me.zhengjie.domain.LocalStorage;
import me.zhengjie.domain.vo.LocalStorageQueryCriteria;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.mapper.LocalStorageMapper;
import me.zhengjie.utils.*;
import me.zhengjie.service.LocalStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;

/**
* @author Zheng Jie
* @date 2019-09-05
*/
@Service
@RequiredArgsConstructor
public class LocalStorageServiceImpl extends ServiceImpl<LocalStorageMapper, LocalStorage> implements LocalStorageService {

    private final LocalStorageMapper localStorageMapper;
    private final FileProperties properties;

    @Override
    public Object queryAll(LocalStorageQueryCriteria criteria, Page<Object> page){
        IPage<LocalStorage> localStorages = localStorageMapper.findAll(criteria, page);
        return PageUtil.toPage(localStorages.getRecords(), localStorages.getTotal());
    }

    @Override
    public List<LocalStorage> queryAll(LocalStorageQueryCriteria criteria){
        return localStorageMapper.findAll(criteria);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalStorage create(String name, MultipartFile multipartFile) {
        FileUtil.checkSize(properties.getMaxSize(), multipartFile.getSize());
        String suffix = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        String type = FileUtil.getFileType(suffix);
        File file = FileUtil.upload(multipartFile, properties.getPath().getPath() + type +  File.separator);
        if(ObjectUtil.isNull(file)){
            throw new BadRequestException("上传失败");
        }
        try {
            name = StringUtils.isBlank(name) ? FileUtil.getFileNameNoEx(multipartFile.getOriginalFilename()) : name;
            LocalStorage localStorage = new LocalStorage(
                    file.getName(),
                    name,
                    suffix,
                    file.getPath(),
                    type,
                    FileUtil.getSize(multipartFile.getSize())
            );
            save(localStorage);
            return localStorage;
        }catch (Exception e){
            FileUtil.del(file);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(LocalStorage resources) {
        LocalStorage localStorage = getById(resources.getId());
        localStorage.copy(resources);
        saveOrUpdate(localStorage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(Long[] ids) {
        for (Long id : ids) {
            LocalStorage storage = getById(id);
            FileUtil.del(storage.getPath());
            removeById(storage);
        }
    }

    @Override
    public void download(List<LocalStorage> queryAll, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LocalStorage localStorageDTO : queryAll) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("文件名", localStorageDTO.getRealName());
            map.put("备注名", localStorageDTO.getName());
            map.put("文件类型", localStorageDTO.getType());
            map.put("文件大小", localStorageDTO.getSize());
            map.put("创建者", localStorageDTO.getCreateBy());
            map.put("创建日期", localStorageDTO.getCreateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }
}