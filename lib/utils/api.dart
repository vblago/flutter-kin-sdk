import 'dart:async';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

class Api {
  Future<http.Response> postRequest(String url, String data) async {
    return await http
        .post(url, body: data, headers: {"Content-Type": "application/json"});
  }

  Future<http.Response> getRequest(String url) async {
    return await http.get(url);
  }

  //   Future<http.Response> postRequest(String url, String data) async {
  //   bool trustSelfSigned = true;
  //   HttpClient httpClient = new HttpClient()
  //     ..badCertificateCallback =
  //         ((X509Certificate cert, String host, int port) => trustSelfSigned);
  //   IOClient ioClient = new IOClient(httpClient);

  //   return await ioClient
  //       .post(url, body: data, headers: {"Content-Type": "application/json"});
  // }

  // Future<http.Response> getRequest(String url) async {
  //   bool trustSelfSigned = true;
  //   HttpClient httpClient = new HttpClient()
  //     ..badCertificateCallback =
  //         ((X509Certificate cert, String host, int port) => trustSelfSigned);
  //   IOClient ioClient = new IOClient(httpClient);

  //   return ioClient.get(url);
  // }
}
