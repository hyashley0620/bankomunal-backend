package com.bankomunal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * Servicio para envío de correos electrónicos.
 * Usa Spring Mail (JavaMailSender) configurado en application.properties.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-url:http://localhost:5500}")
  private String frontendUrl;

  @Value("${spring.mail.username}")
  private String fromEmail;

  /**
   * Envía el correo de recuperación de contraseña con el enlace que contiene el
   * token.
   *
   * @param toEmail Correo del destinatario
   * @param token   UUID del token de recuperación
   * @param nombre  Nombre del usuario para personalizar el correo
   */
  public void enviarCorreoRecuperacion(String toEmail, String token, String nombre) {
    String enlace = frontendUrl + "/reset-password.html?token=" + token;

    String html = """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"></head>
        <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="padding:30px 0;">
            <tr><td align="center">
              <table width="580" cellpadding="0" cellspacing="0"
                     style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                <!-- Header -->
                <tr>
                  <td style="background:linear-gradient(135deg,#0A9396,#005F73);padding:32px;text-align:center;">  
                  <h1 style="color:#ffffff;margin:0;font-size:24px;letter-spacing:1px;">BANKOMUNAL</h1>
                    <p style="color:#94D2BD;margin:6px 0 0;font-size:13px;">Microfinanzas Comunitarias</p>
                  </td>
                </tr>

                <!-- Body -->
                <tr>
                  <td style="padding:36px 40px;">
                    <h2 style="color:#001219;margin:0 0 16px;font-size:20px;">
                      Recuperación de contraseña
                    </h2>
                    <p style="color:#4a4a4a;font-size:15px;line-height:1.6;margin:0 0 12px;">
                      Hola <strong>%s</strong>,
                    </p>
                    <p style="color:#4a4a4a;font-size:15px;line-height:1.6;margin:0 0 24px;">
                      Recibimos una solicitud para restablecer la contraseña de tu cuenta.
                      Haz clic en el botón de abajo para crear una contraseña nueva.
                      Este enlace es válido por <strong>2 horas</strong>.
                    </p>

                    <!-- Botón -->
                    <table cellpadding="0" cellspacing="0" style="margin:0 auto 28px;">
                      <tr>
                        <td style="background:#0A9396;border-radius:8px;">
                          <a href="%s"
                             style="display:inline-block;padding:14px 36px;color:#ffffff;
                                    text-decoration:none;font-weight:700;font-size:15px;">
                            Restablecer contraseña
                          </a>
                        </td>
                      </tr>
                    </table>

                    <p style="color:#777;font-size:13px;line-height:1.6;margin:0 0 8px;">
                      Si el botón no funciona, copia y pega este enlace en tu navegador:
                    </p>
                    <p style="word-break:break-all;background:#f0f4f8;padding:12px;border-radius:6px;
                              font-size:12px;color:#0A9396;margin:0 0 24px;">
                      %s
                    </p>

                    <hr style="border:none;border-top:1px solid #e8e8e8;margin:0 0 20px;">
                    <p style="color:#999;font-size:12px;line-height:1.5;margin:0;">
                      Si no solicitaste este cambio, ignora este correo.
                      Tu contraseña seguirá siendo la misma.<br>
                      Por seguridad, este enlace expirará en 2 horas.
                    </p>
                  </td>
                </tr>

                <!-- Footer -->
                <tr>
                  <td style="background:#f8f9fa;padding:20px 40px;text-align:center;">
                    <p style="color:#aaa;font-size:12px;margin:0;">
                      © 2026 Bankomunal · Todos los derechos reservados
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """
        .formatted(nombre, enlace, enlace);

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(fromEmail, "Bankomunal");
      helper.setTo(toEmail);
      helper.setSubject("Recuperación de contraseña — Bankomunal");
      helper.setText(html, true);
      mailSender.send(message);
      log.info("Correo de recuperación enviado a: {}", toEmail);
    } catch (Exception e) {
      log.error("Error al enviar correo de recuperación a {}: {}", toEmail, e.getMessage());
      // No lanzamos excepción para no revelar si el correo existe o no
    }
  }

  /**
   * Envía el código de verificación en dos pasos (2FA) que el socio debe
   * ingresar para completar el inicio de sesión.
   *
   * @param toEmail Correo del destinatario
   * @param codigo  Código numérico de 6 dígitos
   * @param nombre  Nombre del usuario para personalizar el correo
   */
  public void enviarCodigoMfa(String toEmail, String codigo, String nombre) {
    String html = """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"></head>
        <body style="font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:0;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="padding:30px 0;">
            <tr><td align="center">
              <table width="580" cellpadding="0" cellspacing="0"
                     style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                <tr>
                  <td style="background:linear-gradient(135deg,#0A9396,#005F73);padding:32px;text-align:center;">
                    <h1 style="color:#ffffff;margin:0;font-size:24px;letter-spacing:1px;">BANKOMUNAL</h1>
                    <p style="color:#94D2BD;margin:6px 0 0;font-size:13px;">Microfinanzas Comunitarias</p>
                  </td>
                </tr>

                <tr>
                  <td style="padding:36px 40px;">
                    <h2 style="color:#001219;margin:0 0 16px;font-size:20px;">
                      Código de verificación
                    </h2>
                    <p style="color:#4a4a4a;font-size:15px;line-height:1.6;margin:0 0 20px;">
                      Hola <strong>%s</strong>, usa este código para completar tu inicio de sesión.
                      Es válido por <strong>10 minutos</strong>.
                    </p>

                    <table cellpadding="0" cellspacing="0" style="margin:0 auto 28px;">
                      <tr>
                        <td style="background:#f0f4f8;border:1px dashed #0A9396;border-radius:8px;padding:18px 36px;">
                          <span style="font-size:32px;font-weight:700;letter-spacing:8px;color:#005F73;">%s</span>
                        </td>
                      </tr>
                    </table>

                    <hr style="border:none;border-top:1px solid #e8e8e8;margin:0 0 20px;">
                    <p style="color:#999;font-size:12px;line-height:1.5;margin:0;">
                      Si no intentaste iniciar sesión, cambia tu contraseña de inmediato e
                      ignora este correo.
                    </p>
                  </td>
                </tr>

                <tr>
                  <td style="background:#f8f9fa;padding:20px 40px;text-align:center;">
                    <p style="color:#aaa;font-size:12px;margin:0;">
                      © 2026 Bankomunal · Todos los derechos reservados
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """
        .formatted(nombre, codigo);

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(fromEmail, "Bankomunal");
      helper.setTo(toEmail);
      helper.setSubject("Tu código de verificación — Bankomunal");
      helper.setText(html, true);
      mailSender.send(message);
      log.info("Código MFA enviado a: {}", toEmail);
    } catch (Exception e) {
      log.error("Error al enviar código MFA a {}: {}", toEmail, e.getMessage());
      throw new IllegalStateException("No se pudo enviar el código de verificación. Intenta de nuevo.");
    }
  }
}
