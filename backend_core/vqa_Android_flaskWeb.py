# -*- coding: utf-8 -*-
import argparse, json, io, os, os.path, time, re, base64, yaml
import numpy as np
import torch
import torch.backends.cudnn as cudnn
import torch.nn as nn
from torch.autograd import Variable
import torch.utils.data as data
import torchvision.transforms as transforms
import requests, urllib, socket
from tqdm import tqdm
from preprocessing import image_features_extraction as imf
import models
from PIL import Image
from one_predict_forWeb import predict
from flask import Flask, render_template, request, redirect, url_for, make_response,jsonify
from werkzeug.utils import secure_filename
from datetime import timedelta
from werkzeug.datastructures import CombinedMultiDict, MultiDict
# from VToW_xunfei.asr_Baidu_api import VoiceToWord as VW
from VToW_xunfei.weblfasr_python3_demo.weblfasr_python3_demo import vtow as VW
from VToW_xunfei.translate import translation as tl
from translate import Translator
# # 以下是将简单句子从英语翻译中文
# translator= Translator(to_lang="chinese")
# translation = translator.translate("Good night!")
# print translation

# Load config yaml file
parser = argparse.ArgumentParser()
parser.add_argument('--path_config', default='./config/default.yaml', type=str,
                    help='path to a yaml config file')
args = parser.parse_args()

if args.path_config is not None:
    with open(args.path_config, 'r') as handle:
        config = yaml.load(handle, Loader=yaml.FullLoader)

class ImageDataset(data.Dataset):

    def __init__(self, img_filename, img_filepath, transform=None):  # , path
        # self.path = path
        self.transform = transform
        # Load the paths to the images available in the folder
        img_path = []
        self.filename = img_filename
        self.file_path = img_filepath
        img_path.append(self.file_path)
        self.image_names = img_path

    def __getitem__(self, index):
        item = {}
        item['name'] = self.filename
        item['path'] = self.file_path
        # item['path'] = os.path.join(self.path, item['name'])

        # 使用PIL加载图片数据再提取特征, Image.oopen后维度例如(3, 640, 722)
        item['visual'] = Image.open(item['path']).convert('RGB')
        if self.transform is not None:
            item['visual'] = self.transform(item['visual'])
        return item

    def __len__(self):
        return len(self.image_names)

    def get_path(self):
        return os.path.basename(self.file_path)

def get_transform(img_size):
    return transforms.Compose([
        transforms.Resize(img_size),
        transforms.CenterCrop(img_size),
        transforms.ToTensor(),
        # TODO : Compute mean and std of VizWiz
        # ImageNet normalization
        transforms.Normalize(mean=[0.485, 0.456, 0.406],
                             std=[0.229, 0.224, 0.225]),
    ])
    

"""
Flask后端配置
"""
app = Flask(__name__)
# 设置静态文件缓存过期时间
app.send_file_max_age_default = timedelta(seconds=1)
app.config['SQLALCHEMY_COMMIT_ON_TEARDOWN']=True
app.config['SQLALCHEMY_TRACK_MODIFICATIONS']=True
# _ = requests.post("/your/server/url", json=res)   
# _ = requests.post("/your/server/url", data=json.dumps(res))  # 如果服务器端获取的方式为data

'''
VQA预测模型模块
'''
#设置允许的文件格式  
ALLOWED_EXTENSIONS = set(['png', 'jpg', 'JPG', 'PNG', 'bmp', 'jpeg'])
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS

flag = 1
def NumToEng(ans):
    if ans=="1": return "one" 
    if ans=="2": return "two"
    if ans=="3": return "three" 
    if ans=="4": return "four"
    if ans=="5": return "five" 
    if ans=="6": return "six"
    if ans=="7": return "seven" 
    if ans=="8": return "eight"
    if ans=="9": return "nine" 
    if ans=="10": return "ten"
    else: return ans
    
# 先接收用户上传的图片
@app.route('/register/', methods=['POST','GET'])  # 添加路由  'POST','GET'
def upload2():
    # return "<h1 style='color:blue'>Hello There!</h1>"
    global flag
    if request.headers.get('X-Forwarded-For'):  #使用nginx反向代理的时候，传过来的ip都为本地ip
        ip_address = request.headers['X-Forwarded-For']
    elif request.headers.get('X-Real-IP'):
        ip_address = request.headers.get('X-Real-IP')
    else:
        ip_address = request.remote_addr
    ip_address = ip_address.split(",")[0]   
        
    all_data = request.files
    print(all_data)
    all_data = all_data.to_dict()
    f = all_data['image']
    # fig = request.files('image')
    p = os.path.dirname(__file__)  # 当前文件所在路径
    # 图片路径用于PIL加载图片的RGB数据，图片名字无所谓
    img_filename = 'vqa_test_%s.jpg'%ip_address   
    #在APP登录中可以用用户名标记字段 但仅以"question"接收 而问题开头标上用户名 但对于图片不好操作 要有数据库

    upload_path = os.path.join(p, 'static', secure_filename(img_filename))
    f.save(upload_path)

    transform = get_transform(config['images']['img_size'])
    dataset = ImageDataset(img_filename, upload_path, transform=transform)
    att_fea, noatt_fea, img_namee = imf.feature(dataset)
    # print(att_fea.shape)
    
    np.save('./prepro_data/att_fea_%s.npy'%ip_address, att_fea)
    np.save('./prepro_data/noatt_fea_%s.npy'%ip_address, noatt_fea)
    np.save('./prepro_data/img_name_%s.npy'%ip_address, img_namee)
    
    flag = 1  #每次上传新的图片就重置问题编号flag
    return jsonify({"data":"yes"})

def cleantxt(raw):
    pattern = re.compile(r'[\u4e00-\u9fa5]')
    return  re.sub(pattern,"",raw)

@app.route('/question/', methods=['POST','GET'])  # 添加路由  'POST','GET'
def upload1():
    global flag
    try:
        if request.headers.get('X-Forwarded-For'):  #使用nginx反向代理的时候，传过来的ip都为本地ip
            ip_address = request.headers['X-Forwarded-For']
        elif request.headers.get('X-Real-IP'):
            ip_address = request.headers.get('X-Real-IP')
        else:
            ip_address = request.remote_addr
        ip_address = ip_address.split(",")[0]  
         
        userip = ip_address.split(".")[0] + ip_address.split(".")[1] +ip_address.split(".")[2]+ip_address.split(".")[3]
        question = request.form.get('question%s%s'%(userip, str(flag)))  # 从APP客户端获取问题字符串
        # question = request.form.get('question1921682322%s'%(str(flag)))
        print('---------------question%s----------------'%(str(flag)))
            
        translator= Translator(from_lang="chinese",to_lang="english")  #键盘语音输入中文则需翻译
        question = translator.translate(question)
        print(question)
        flag = flag + 1
        
        p = os.path.dirname(__file__)
        img_filename = 'vqa_test_%s.jpg'%ip_address
        upload_path = os.path.join(p, 'static', secure_filename(img_filename))
        att_fea = np.load('prepro_data/att_fea_%s.npy'%ip_address)
        noatt_fea = np.load('prepro_data/noatt_fea_%s.npy'%ip_address)
        img_namee = np.load('prepro_data/img_name_%s.npy'%ip_address)
        res = predict(img_filename, question, att_fea, noatt_fea, img_namee)
        res = NumToEng(res)
        print(res)
        return res
        pass
    except Exception as e:
        print('except:', e)

    # WS(res);   
    # 将回答转换为语音的最大问题是语音数据格式，是amr、MP3还是二进制数据
    # 使用讯飞的合成API，在真机上调试，放弃
    # 已解决直接点击播放消息框，直接用import android.speech.tts.TextToSpeech
    # 新增的语音识别、转换模块，但是对于盲人，加入语音唤醒功能会更完善，考虑AIsound
    # 安卓模拟器是X86架构，无法运行arm架构的讯飞demo

@app.route('/voiceQues/', methods=['POST','GET'])  # 添加路由  '
def upload3():
    global flag
    if request.headers.get('X-Forwarded-For'):  #使用nginx反向代理的时候，传过来的ip都为本地ip
        ip_address = request.headers['X-Forwarded-For']
    elif request.headers.get('X-Real-IP'):
        ip_address = request.headers.get('X-Real-IP')
    else:
        ip_address = request.remote_addr
    ip_address = ip_address.split(",")[0]   
        
    all_data = request.files
    print(all_data)
    userip = ip_address.split(".")[0] + ip_address.split(".")[1] +ip_address.split(".")[2]+ip_address.split(".")[3]
    question = request.form.get('question%s'%(userip)) 
    flag = flag + 1
    p = os.path.dirname(__file__)  # 当前文件所在路径
    all_data = all_data.to_dict()
    f = all_data['voice_ques%s'%userip]   #'question1921682322'
    # 百度api的文件后缀只支持 pcm/wav/amr 格式，极速版额外支持m4a格式
    voice_filename = 'testvoice_%s.wav'%ip_address

    upload_path = os.path.join(p, 'static', secure_filename(voice_filename))
    f.save(upload_path)

    # 新增的客户端语音转文字功能,upload_path是下载语音文件的路径
    # 语音转文字的TXT结果已生成，每次写覆盖，进行中文及符号过滤，仅保留英文
    # 这里安卓客户端输入中文语音问题，在后台调用有道api翻译为英文问题
    xunfei = ""
    res = ""
    voice_ques = ""
    try:
        xunfei = VW(upload_path)
        voice_ques = tl(xunfei)   # cleantxt(VW(upload_path))
        # for tag in ['，', '。', '！', '？', '：', '；']:
        #     if tag in voice_ques:
        #         voice_ques = voice_ques.replace(tag, ''); 
        print(voice_ques)
        img_filename = 'vqa_test_%s.jpg'%ip_address
        att_fea = np.load('prepro_data/att_fea_%s.npy'%ip_address)
        noatt_fea = np.load('prepro_data/noatt_fea_%s.npy'%ip_address)
        img_namee = np.load('prepro_data/img_name_%s.npy'%ip_address)
        res = predict(img_filename, voice_ques, att_fea, noatt_fea, img_namee)
        # 文字转语音使用的是内置接口
        # D:\Android_SDK\platforms\android-24\android.jar\android\speech\tts\TextToSpeech.class
        # 将回答转换为语音的最大问题是语音数据格式，是amr、MP3还是二进制数据
        # 使用讯飞的合成API，在真机上调试，放弃
        # 已解决直接点击播放消息框，直接用import android.speech.tts.TextToSpeech
        print("Q: "+voice_ques+"\n"+"A: "+res)
        res = xunfei + "\n" + voice_ques + "\n" + res
        pass
    except Exception as e:
        print('except:', e)

    return res


@app.route('/translate/', methods=['POST','GET'])  # 添加路由  'POST','GET'
def upload4():
    global flag
    if request.headers.get('X-Forwarded-For'):  #使用nginx反向代理的时候，传过来的ip都为本地ip
        ip_address = request.headers['X-Forwarded-For']
    elif request.headers.get('X-Real-IP'):
        ip_address = request.headers.get('X-Real-IP')
    else:
        ip_address = request.remote_addr
    ip_address = ip_address.split(",")[0] 
      
    userip = ip_address.split(".")[0] + ip_address.split(".")[1] +ip_address.split(".")[2]+ip_address.split(".")[3]
    question = request.form.get('question%s'%(userip))  
    # question = request.form.get('question1921682322%s'%(userip))
    print(question)
    try:
        res = tl(question)
        print(res)
        return res
        pass
    except Exception as e:
        print('except:', e)
        

if __name__ == "__main__":
    # app.debug = True
    app.run(host='0.0.0.0', port=8880, debug=True)
