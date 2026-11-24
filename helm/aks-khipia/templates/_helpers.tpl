{{- define "aks-khipia.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "aks-khipia.labels" -}}
app.kubernetes.io/name: {{ include "aks-khipia.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "aks-khipia.selectorLabels" -}}
app.kubernetes.io/name: {{ include "aks-khipia.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
