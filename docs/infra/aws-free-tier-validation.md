# Validación de recursos AWS — CircleGuard

## Objetivo

Validar si la cuenta AWS permite crear la plataforma CI/CD de la Parte 1 con dos instancias EC2.

## Topología aprobada

| Instancia | Tipo | vCPU | RAM | Disco | Componentes |
|---|---|---:|---:|---:|---|
| `cg-tools` | `t3.large` | 2 | 8 GB | 60 GB gp3 | Jenkins, SonarQube, PostgreSQL, Nexus |
| `cg-envs` | `t3.medium` | 2 | 4 GB | 40 GB gp3 | dev, stage y prod: gateway + Redis |

## Checklist

- [ ] Cuenta AWS activa y método de pago o créditos disponibles.
- [ ] Región definida (`AWS_REGION`).
- [ ] Capa gratuita vigente verificada en la consola.
- [ ] Créditos AWS Academy/Educate o presupuesto aprobado.
- [ ] Alarma de presupuesto creada en AWS Budgets.
- [ ] Cuota de instancias `t3.large` y `t3.medium` disponible.
- [ ] Key pair creado y almacenado fuera del repositorio.
- [ ] CIDR administrativo definido (`ADMIN_CIDR`).
- [ ] IP del docente identificada para los Security Groups.

## Estimación de costo

| Recurso | 24/7 | 12 h/día laboral |
|---|---:|---:|
| `cg-tools` t3.large | ~US$60/mes | ~US$22/mes |
| `cg-envs` t3.medium | ~US$30/mes | ~US$11/mes |
| EBS 100 GB gp3 | ~US$8/mes | ~US$8/mes |
| **Total** | **~US$98/mes** | **~US$41/mes** |

> La capa gratuita estricta no alcanza: `t3.micro` tiene 1 GB de RAM y no soporta SonarQube + Nexus + Jenkins. Se requieren créditos o presupuesto.

## Comandos de verificación

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
./infra/bootstrap/00-check-prerequisites.sh
```

## Evidencia requerida

- Salida de `aws sts get-caller-identity`.
- Salida de `aws ec2 describe-instance-types`.
- Captura de AWS Budgets.
- Captura de la capa gratuita o créditos disponibles.
